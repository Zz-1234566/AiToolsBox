package com.example.aitools.service.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.aitools.common.BackgroundColorEnum;
import com.example.aitools.common.ResultCode;
import com.example.aitools.config.SegmentationConfig;
import com.example.aitools.dto.SegmentResult;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.exception.ErrorFactory;
import com.example.aitools.service.SegmentationService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Iterator;

/**
 * 人像分割服务实现：本地 ONNX Runtime 加载 MODNet（modnet_photographic_portrait_matting），CPU 推理。
 * <p>
 * <b>预处理严格对齐 ZHKKKe/MODNet 官方</b>（{@code src/model.py}）：固定 {@code mean=[0.5,0.5,0.5]}、
 * {@code std=[0.5,0.5,0.5]}，像素除以 255 后再中心化到 {@code [-1,1]}。三个最易踩错的点：
 * <ol>
 *   <li><b>归一化除数是「固定 255」而非整图 maxPix</b>。MODNet 官方就是固定 255；写 {@code maxPix}
 *       （rembg/u2netp 习惯）会让蒙版整体偏移，头发丝检测率显著下降。</li>
 *   <li><b>STD 必须是 0.5/0.5/0.5</b>。写 1.0/1.0/1.0 不会报错但效果差——只做了中心化没真正归一化，
 *       蒙版会偏向过曝区域。</li>
 *   <li><b>取张量值必须走 {@code getFloatBuffer()} 而非 {@code getValue()}</b>。
 *       4 维张量的 {@code getValue()} 返回的是嵌套数组，强转 {@code float[]} 会
 *       ClassCastException；{@code getFloatBuffer()} 拿到的是扁平 buffer，NCHW 布局下按行主序排布。</li>
 * </ol>
 *
 * <b>线程模型</b>：{@code OrtSession} 本身线程安全（ONNX Runtime 官方保证 run 可并发调用），
 * 因此单例持有；每次推理的输入张量、输出 buffer 全部是方法内局部变量，天然不共享，
 * 不会出现「A 请求读到 B 请求蒙版」的串数据问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentationServiceImpl implements SegmentationService {

    private final SegmentationConfig segmentationConfig;

    /**
     * ONNX 全局环境（进程内单例，由 ONNX Runtime 自己按 JVM 生命周期管理）。
     * <p>
     * 不手动 close：{@code OrtEnvironment} 关闭后同 JVM 内无法重建，交给 Runtime 兜底。
     */
    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();

    /**
     * 模型会话单例（懒加载 + 双检锁）。
     * <p>
     * <b>为什么必须是单例</b>：新建一次 {@code OrtSession} 要把 4.4MB 模型读进来并建图，
     * 实测约 2 秒；而推理本身只要 146ms。若每次请求都新建 session，
     * 一次请求的开销会有 93% 花在加载模型上，且并发时内存里同时躺着 N 份模型。
     * <p>
     * <b>为什么懒加载</b>：模型加载要 2 秒，如果放在构造期会拖慢整个应用启动，
     * 且在 {@code segmentation.enabled=false} 的环境里属于无谓开销。
     * 用 volatile + 双检锁保证只初始化一次，初始化失败也只记一次日志。
     */
    private volatile OrtSession session;

    /** 模型输入节点名，懒加载时从 session 动态取一次后缓存 */
    private volatile String inputName;

    /** MODNet 均值（R/G/B，归一化 0~1 空间），ZHKKKe/MODNet 官方预处理 */
    private static final float[] MEAN = {0.5f, 0.5f, 0.5f};

    /** MODNet 标准差（R/G/B，归一化 0~1 空间），ZHKKKe/MODNet 官方预处理（=0.5/0.5/0.5） */
    private static final float[] STD = {0.5f, 0.5f, 0.5f};

    private static final String OUTPUT_MIME = "image/png";

    @Override
    public SegmentResult removeBackground(byte[] imageBytes, String backgroundColor) {
        // 1) 前置校验：总开关 + 入参
        if (!Boolean.TRUE.equals(segmentationConfig.getEnabled())) {
            throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请先选择要处理的证件照");
        }
        // 底色在入口就解析：非法值早失败，避免白白跑完 146ms 推理再报错
        BackgroundColorEnum color = BackgroundColorEnum.fromCode(backgroundColor);

        BufferedImage source = decodeWithPixelLimit(imageBytes);

        int width = source.getWidth();
        int height = source.getHeight();

        // 2) 小图先放大（不动大图）：300px 头像放大到 640 再送 320，模型看到的细节翻 4 倍
        BufferedImage upscaled = upscaleIfTooSmall(source);

        // 3) 预处理：缩放到模型输入尺寸 → 归一化 → NCHW
        int inputSize = segmentationConfig.getInputSize();
        BufferedImage resized = resize(upscaled, inputSize, inputSize);
        float[] input = toInputTensor(resized, inputSize);

        // 4) 推理 → 0~255 灰度蒙版（模型尺寸）
        int[] mask = predictMask(input, inputSize);

        // 5) 蒙版二值化 + 羽化：让 alpha 边缘平滑（头发丝自然）。
        //    走 yml 配置（公开端点不接受用户参数，与 image-bg-remover 工具分流）。
        int[] maskRefined = refineMask(mask, inputSize,
                segmentationConfig.getMaskThreshold(), segmentationConfig.getFeatherSize());

        // 6) 蒙版双线性放大回原图尺寸 + 按 alpha 混合底色
        byte[] output = composite(source, maskRefined, inputSize, color);

        return new SegmentResult(output, width, height, OUTPUT_MIME);
    }

    @Override
    public SegmentResult removeBackgroundTransparent(byte[] imageBytes) {
        // 1) 前置校验：总开关 + 入参
        if (!Boolean.TRUE.equals(segmentationConfig.getEnabled())) {
            throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请先选择要处理的图片");
        }

        BufferedImage source = decodeWithPixelLimit(imageBytes);
        int width = source.getWidth();
        int height = source.getHeight();

        // 2) 小图先放大（不动大图）：保留发丝级细节
        BufferedImage upscaled = upscaleIfTooSmall(source);

        // 3) 预处理 → 推理 → 透明合成
        int inputSize = segmentationConfig.getInputSize();
        BufferedImage resized = resize(upscaled, inputSize, inputSize);
        float[] input = toInputTensor(resized, inputSize);
        int[] mask = predictMask(input, inputSize);
        // 蒙版二值化 + 羽化（走 yml 配置）
        int[] maskRefined = refineMask(mask, inputSize,
                segmentationConfig.getMaskThreshold(), segmentationConfig.getFeatherSize());
        byte[] output = compositeTransparent(source, maskRefined, inputSize);

        return new SegmentResult(output, width, height, OUTPUT_MIME);
    }

    @Override
    public SegmentResult changeBackground(byte[] foregroundImage, byte[] backgroundImage) {
        if (foregroundImage == null || foregroundImage.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请上传要处理的图片");
        }
        if (backgroundImage == null || backgroundImage.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请上传背景图");
        }

        // 1) 前景去背景 → 透明 PNG
        SegmentResult fg = removeBackgroundTransparent(foregroundImage);
        BufferedImage fgImg = bytesToImage(fg.getImageBytes());
        // 2) 背景图解码（不做去背景）
        BufferedImage bgImg = decodeWithPixelLimit(backgroundImage);

        // 3) 合成：取 fg / bg 宽高的较大值，bg 拉伸铺底 + fg 等比缩放居中
        int W = Math.max(fgImg.getWidth(), bgImg.getWidth());
        int H = Math.max(fgImg.getHeight(), bgImg.getHeight());

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            // 背景：拉伸铺满
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(bgImg, 0, 0, W, H, null);
            // 前景：等比缩放居中
            int fw = fgImg.getWidth();
            int fh = fgImg.getHeight();
            double scale = Math.min((double) W / fw, (double) H / fh);
            int dw = (int) Math.round(fw * scale);
            int dh = (int) Math.round(fh * scale);
            int dx = (W - dw) / 2;
            int dy = (H - dh) / 2;
            g.drawImage(fgImg, dx, dy, dw, dh, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(out, "jpg", baos);
        } catch (IOException e) {
            log.error("[image-bg-remover] 合成 JPG 编码失败", e);
            throw ErrorFactory.of(ResultCode.IMAGE_TOOL_FAILED);
        }
        return new SegmentResult(baos.toByteArray(), W, H, "image/jpeg");
    }

    @Override
    public SegmentResult changeBackground(byte[] foregroundImage, String backgroundColor) {
        // 纯色合成的快捷入口：输出 TYPE_INT_RGB PNG
        return removeBackground(foregroundImage, backgroundColor);
    }

    @Override
    public SegmentResult processImage(byte[] imageBytes, String mode,
                                      String backgroundColor, byte[] backgroundImage) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请先选择要处理的图片");
        }
        if (mode == null || mode.isBlank()) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请指定处理模式（transparent/color/image）");
        }

        // mode 标准化：忽略大小写
        String m = mode.trim().toLowerCase(java.util.Locale.ROOT);
        switch (m) {
            case "transparent":
                // 单纯去背景，输出透明 PNG（走 yml 配置：mask-threshold / feather-size）
                return removeBackgroundTransparent(imageBytes);
            case "color":
                if (backgroundColor == null || backgroundColor.isBlank()) {
                    throw ErrorFactory.of(ResultCode.PARAM_MISSING, "color 模式必须指定底色（red/blue/white）");
                }
                return removeBackground(imageBytes, backgroundColor);
            case "image":
                if (backgroundImage == null || backgroundImage.length == 0) {
                    throw ErrorFactory.of(ResultCode.PARAM_MISSING, "image 模式必须上传背景图");
                }
                // 走「透明 PNG + 叠到 bg」路径
                return changeBackground(imageBytes, backgroundImage);
            default:
                throw ErrorFactory.of(ResultCode.PARAM_ERROR,
                        "mode 必须是 transparent / color / image，当前: " + mode);
        }
    }

    // ==================== 解码 ====================

    /**
     * 解码图片，并按「像素总数」而非字节数设限。
     * <p>
     * 关键点：必须用 {@link ImageReader} 先只读宽高做判断，再真正解码。
     * 一张 10000×8000 的 JPEG 压缩后可能只有 2MB，但 ARGB 像素缓冲要 320MB，
     * 若等到 {@code ImageIO.read} 之后才发现就晚了，OOM 发生时还没机会抛业务异常。
     *
     * @param imageBytes 原始图片字节
     * @return 解码后的图片
     */
    private BufferedImage decodeWithPixelLimit(byte[] imageBytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw ErrorFactory.of(ResultCode.IMAGE_UNSUPPORTED,
                        "图片无法解析，请上传 JPG / PNG / BMP 格式的证件照");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                long maxPixels = segmentationConfig.getMaxImagePixels();
                if (maxPixels > 0 && (long) w * h > maxPixels) {
                    log.warn("[segment] 图片像素数超限 w={} h={} pixels={} max={}", w, h, (long) w * h, maxPixels);
                    throw ErrorFactory.of(ResultCode.IMAGE_TOO_LARGE);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw ErrorFactory.of(ResultCode.IMAGE_UNSUPPORTED,
                            "图片无法解析，请上传 JPG / PNG / BMP 格式的证件照");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[segment] 图片解码失败 size={}", imageBytes.length, e);
            throw ErrorFactory.of(ResultCode.IMAGE_UNSUPPORTED,
                    "图片无法解析，请上传 JPG / PNG / BMP 格式的证件照");
        }
    }

    // ==================== 预处理 ====================

    /**
     * 小图先放大到最小边长，再送到模型做 resize。
     * <p>
     * 证件照 / 头像常常只有 300~600px，直接 resize 到 320×320 会丢光发丝级细节。
     * 先放大到 minInputSize（默认 640），模型 resize 后能保留 4 倍细节量，头发丝检出率显著提升。
     * <p>
     * 大图（maxSide ≥ minInputSize）原样返回，避免无意义放大造成性能浪费。
     * <p>
     * 用 {@link Image#SCALE_SMOOTH}（双线性）足够；放大场景不需要更复杂的算法。
     *
     * @param src 原始图片
     * @return 放大后的图片（如果原图已经够大就还是原图）
     */
    private BufferedImage upscaleIfTooSmall(BufferedImage src) {
        int minInput = segmentationConfig.getMinInputSize();
        if (minInput <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide >= minInput) {
            return src;
        }
        double scale = (double) minInput / maxSide;
        int newW = (int) Math.round(w * scale);
        int newH = (int) Math.round(h * scale);
        Image scaled = src.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage bi = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(scaled, 0, 0, null);
        } finally {
            g.dispose();
        }
        log.debug("[segment] 小图放大 {}x{} → {}x{}", w, h, newW, newH);
        return bi;
    }

    /**
     * 蒙版二值化 + 边缘羽化：让 alpha 输出连续而非离散。
     * <p>
     * <b>二值化</b>：按 {@code maskThreshold}（0~1）把低于阈值的像素归零，高于的保留原灰度。
     * 完全的二值（0/255）会让边缘有可见锯齿，所以这里只把背景一侧清成 0，前景一侧保持灰度，
     * 这样前景内部仍然是连续的灰度图，羽化才能起作用。
     * <p>
     * <b>羽化</b>：用 {@link ConvolveOp} 做 {@code (2r+1)×(2r+1)} 均值卷积（r = featherSize）。
     * 320×320 蒙版做 3×3 卷积 ≈ 92 万次循环，CPU 上 < 10ms；比手写循环快且不会越界。
     * <p>
     * <b>形状保留</b>：ConvolveOp 会因为 EDGE_NO_OP 把边缘忽略（输出边缘值会变小），
     * 但模型蒙版的边缘本来就接近背景（maskThreshold=0.5），影响可忽略。
     *
     * @param mask       模型尺寸（{@code inputSize*inputSize}）的 0~255 灰度蒙版
     * @param size       蒙版边长
     * @param threshold  0~1 二值化阈值（≤0 视作不门控）
     * @param feather    羽化半径像素（≤0 视作不羽化）
     * @return 同样尺寸但边缘平滑后的灰度蒙版
     */
    private int[] refineMask(int[] mask, int size, double threshold, int feather) {
        if (threshold <= 0 && feather <= 0) {
            return mask;
        }

        // 把 int[] 数组转成 BufferedImage（灰度图），用 TYPE_BYTE_GRAY 让 ConvolveOp 输出干净
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        // 第一步：按阈值清理背景侧。阈值（0~1）转 0~255 后用「不低于阈值则保留原始灰度」策略，
        // 避免完全二值化导致后续卷积在边缘产生可见振铃。
        if (threshold > 0) {
            int thr255 = (int) Math.round(Math.min(1.0, Math.max(0.0, threshold)) * 255);
            byte[] pixels = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < mask.length; i++) {
                pixels[i] = mask[i] < thr255 ? 0 : (byte) mask[i];
            }
        } else {
            byte[] pixels = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < mask.length; i++) {
                pixels[i] = (byte) mask[i];
            }
        }

        // 第二步：均值卷积羽化
        if (feather > 0) {
            int r = feather;
            int k = 2 * r + 1;
            float[] kernel = new float[k * k];
            Arrays.fill(kernel, 1.0f / (k * k));
            BufferedImage blurred = new ConvolveOp(new Kernel(k, k, kernel),
                    ConvolveOp.EDGE_NO_OP, null).filter(img, null);
            img = blurred;
        }

        // 拷回 int[]：composite/compositeTransparent 仍然按 0~255 灰度读取
        byte[] pixels = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();
        int[] out = new int[mask.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = pixels[i] & 0xFF;
        }
        return out;
    }

    /**
     * 把图片转成 NCHW 排布的 float 数组并按 ZHKKKe/MODNet 官方预处理归一化。
     * <p>
     * 归一化公式 {@code (v / 255 - mean[c]) / std[c]}，{@code mean=[0.5,0.5,0.5]}、
     * {@code std=[0.5,0.5,0.5]}，最终把像素压到 {@code [-1, 1]}。这是 MODNet 官方推荐写法，
     * 与 rembg/u2netp 的「整图 maxPix 做除数」不同——后者是给 U²-Net 用的，不能套到 MODNet 上。
     *
     * @param image     已缩放到模型尺寸的图片
     * @param size      边长
     * @return 长度 {@code 3 * size * size} 的 float 数组，索引顺序 C=0,H=1,W=2
     */
    private float[] toInputTensor(BufferedImage image, int size) {
        int planeSize = size * size;
        float[] tensor = new float[3 * planeSize];

        // MODNet 官方：固定除以 255，把像素压到 [0, 1] 后再做中心化
        final float inv255 = 1.0f / 255.0f;
        // 按通道各写一张平面（NCHW）
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = image.getRGB(x, y);
                int idx = y * size + x;
                tensor[idx]                    = ((((rgb >> 16) & 0xFF) * inv255) - MEAN[0]) / STD[0];
                tensor[planeSize + idx]        = ((((rgb >>  8) & 0xFF) * inv255) - MEAN[1]) / STD[1];
                tensor[2 * planeSize + idx]    = ((( rgb         & 0xFF) * inv255) - MEAN[2]) / STD[2];
            }
        }
        return tensor;
    }

    // ==================== 推理 ====================

    /**
     * 执行推理，返回 {@code inputSize * inputSize} 的 0~255 灰度蒙版。
     *
     * @param input     NCHW 输入数据
     * @param inputSize 模型输入边长
     * @return 灰度蒙版（一维数组，行优先）
     */
    private int[] predictMask(float[] input, int inputSize) {
        OrtSession ortSession = getSession();
        long[] shape = {1, 3, inputSize, inputSize};
        try (OnnxTensor tensor = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(input), shape);
             OrtSession.Result result = ortSession.run(java.util.Map.of(inputName, tensor))) {

            OnnxTensor output = (OnnxTensor) result.get(0);

            // shape 强校验：[N, C, H, W] 必须是 4 维，且 H/W 与配置一致。
            // 换成别的分割模型（如 1024 输入的 u2net_h）时此处会直接失败，
            // 好过让错位的蒙版去污染合成结果。
            long[] outShape = output.getInfo().getShape();
            if (outShape == null || outShape.length != 4) {
                throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE, "分割模型输出格式异常，请联系管理员");
            }
            if (outShape[2] != inputSize || outShape[3] != inputSize) {
                log.error("[segment] 模型输出 shape 不匹配 actual={}", java.util.Arrays.toString(outShape));
                throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE, "分割模型输出尺寸异常，请联系管理员");
            }

            // 必须用扁平 FloatBuffer：getValue() 对 4 维张量返回嵌套数组，
            // 强转 float[] 会 ClassCastException
            FloatBuffer buffer = output.getFloatBuffer();
            int planeSize = inputSize * inputSize;

            // 取第 0 个通道（outShape[1] 恒为 1，但下标 0 兼容多通道侧输出模型）
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < planeSize; i++) {
                float v = buffer.get(i);
                if (v < min) min = v;
                if (v > max) max = v;
            }

            // 用实际 min/max 做归一化（rembg u2net.py 的做法），再 *255 转灰度蒙版
            float range = max - min;
            int[] mask = new int[planeSize];
            if (range <= 0f) {
                // 全图同一值（纯色图 / 模型无输出）：没有前景可言，整图判为背景
                log.warn("[segment] 模型输出为常量值，按全背景处理");
                return mask;
            }
            for (int i = 0; i < planeSize; i++) {
                float norm = (buffer.get(i) - min) / range;
                mask[i] = Math.round(Math.min(1f, Math.max(0f, norm)) * 255f);
            }
            return mask;
        } catch (OrtException e) {
            log.error("[segment] ONNX 推理失败", e);
            throw ErrorFactory.of(ResultCode.SEGMENT_INFER_FAILED);
        }
    }

    /**
     * 取（懒加载）模型会话单例。
     *
     * @return 已就绪的 OrtSession
     */
    private OrtSession getSession() {
        OrtSession local = session;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (session == null) {
                session = createSession();
                // 输入节点名动态取，不硬编码：实测该模型叫 "input.1"，
                // 但 ONNX 图里的节点名随导出工具/版本变化，写死会在换模型时静默失效
                inputName = session.getInputNames().iterator().next();
                log.info("[segment] ONNX 模型加载完成 input={} outputs={}",
                        inputName, session.getOutputNames());
            }
            return session;
        }
    }

    /**
     * 创建 {@code OrtSession}。
     * <p>
     * classpath 资源（模型打进 jar）走 {@code byte[]} 构造器，
     * 因为 {@code createSession(String)} 只接受真实文件路径，jar 内模型没有独立路径；
     * 外挂文件模型走路径构造器，让 Runtime 自己做内存映射、少占一份堆。
     */
    private OrtSession createSession() {
        String modelPath = segmentationConfig.getModelPath();
        try {
            Resource resource = new DefaultResourceLoader().getResource(modelPath);
            if (!resource.exists()) {
                log.error("[segment] 模型文件不存在 path={}", modelPath);
                throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE);
            }
            OrtSession created;
            if (resource.isFile() && "file".equals(resource.getURL().getProtocol())) {
                created = ENV.createSession(resource.getFile().getAbsolutePath());
            } else {
                try (InputStream in = resource.getInputStream()) {
                    created = ENV.createSession(in.readAllBytes());
                }
            }
            return created;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[segment] ONNX 模型加载失败 path={}", modelPath, e);
            throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE);
        }
    }

    /** 应用关闭时释放模型内存，避免重启期间内存被旧进程占着 */
    @PreDestroy
    public void destroy() {
        OrtSession local = session;
        if (local != null) {
            try {
                local.close();
            } catch (OrtException e) {
                log.warn("[segment] 关闭 ONNX session 失败", e);
            }
        }
    }

    // ==================== 缩放 ====================

    /**
     * 缩放图片，逼近 LANCZOS 的质量。
     * <p>
     * <b>为什么不能直接一行搞定</b>：Java 的 {@link RenderingHints} 只有
     * NEAREST / BILINEAR / BICUBIC，<b>没有 LANCZOS</b>（rembg 用的是 PIL 的
     * {@code Image.LANCZOS}）。一步 BICUBIC 从 4000px 直接缩到 320px，
     * 每个输出像素要跨越 12 个源像素，单次插值公式已经超出有效范围，会产生明显振铃。
     * <p>
     * <b>这里的做法</b>：先用 BILINEAR <b>逐次减半</b>把大图缩到目标尺寸的 2 倍以内
     * （每步跨度都是 2:1，双线性完全够用，且有平均降噪效果），
     * 最后一步用 BICUBIC 收尾逼近 LANCZOS。这正是 PIL 内部 {@code reduce()} 的同款策略。
     */
    private BufferedImage resize(BufferedImage src, int targetW, int targetH) {
        if (src.getWidth() == targetW && src.getHeight() == targetH) {
            return src;
        }
        BufferedImage cur = src;
        // 逐步减半，直到两个维度都进入「目标尺寸的 2 倍以内」，最后一步 BICUBIC 收尾。
        // 终止性保证：nw/nh 恒 ≤ 当前尺寸的一半，且被 max(目标, ...) 夹住不会缩到 0，
        // 故每轮严格递减且有下界，循环必然在 O(log(原尺寸/目标)) 步内退出。
        while (cur.getWidth() > targetW * 2 || cur.getHeight() > targetH * 2) {
            int nw = Math.max(targetW, cur.getWidth() / 2);
            int nh = Math.max(targetH, cur.getHeight() / 2);
            // 先判「本轮是否已无法缩小」，再绘制。
            // 反过来写会在缩放成功后 nw==cur.getWidth() 恒成立，导致每轮都提前 break，
            // 大图只减半一次就交给 BICUBIC，跨度 6:1 以上必然出现振铃。
            if (nw == cur.getWidth() && nh == cur.getHeight()) {
                break;
            }
            cur = drawScaled(cur, nw, nh, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return drawScaled(cur, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** 按指定插值方式缩放；尺寸相同时原样返回 */
    private BufferedImage drawScaled(BufferedImage src, int w, int h, Object interpolation) {
        if (src.getWidth() == w && src.getHeight() == h) {
            return src;
        }
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    // ==================== 合成 ====================

    /**
     * 蒙版放大回原图 + 合成底色。
     * <p>
     * 蒙版放大用<b>手写双线性</b>而不是 {@code Graphics2D} 拉伸：Graphics2D 会把
     * 320×320 的灰度图先落成一张 {@code w×h} 的 BufferedImage，对 4000 万像素的图
     * 就是多分配 160MB；而这里只需要按行采样出 alpha，额外内存只有 O(宽)。
     * 另外<b>绝不能用最近邻</b>，320→2000 的放大用最近邻会出现明显的方块锯齿。
     * <p>
     * 合成公式即标准 alpha 混合（底色不透明）：{@code out = bg + (fg - bg) * a}
     *
     * @param source    原图
     * @param mask      模型尺寸的 0~255 蒙版
     * @param maskSize  蒙版边长
     * @param color     底色
     * @return PNG 字节
     */
    private byte[] compositeTransparent(BufferedImage source, int[] mask, int maskSize) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rowBuf = new int[width];

        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, rowBuf, 0, width);

            float fy = (y + 0.5f) * maskSize / height - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, maskSize - 1);
            int y1 = Math.min(y0 + 1, maskSize - 1);
            float wy = fy - y0;

            for (int x = 0; x < width; x++) {
                float fx = (x + 0.5f) * maskSize / width - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, maskSize - 1);
                int x1 = Math.min(x0 + 1, maskSize - 1);
                float wx = fx - x0;

                float alpha = bilerp(mask, maskSize, x0, y0, x1, y1, wx, wy) / 255f;
                int srcArgb = rowBuf[x];
                alpha *= ((srcArgb >>> 24) & 0xFF) / 255f;

                int sr = (srcArgb >> 16) & 0xFF;
                int sg = (srcArgb >> 8) & 0xFF;
                int sb = srcArgb & 0xFF;
                int aOut = clamp255(alpha * 255f);
                rowBuf[x] = (aOut << 24) | (sr << 16) | (sg << 8) | sb;
            }
            out.setRGB(0, y, width, 1, rowBuf, 0, width);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(out, "png", baos);
        } catch (IOException e) {
            log.error("[segment] 透明 PNG 编码失败", e);
            throw ErrorFactory.of(ResultCode.IMAGE_TOOL_FAILED);
        }
        return baos.toByteArray();
    }

    /**
     * 字节流 → BufferedImage（PNG 解码）。仅本类内部复用，避免重复 try-catch。
     */
    private BufferedImage bytesToImage(byte[] bytes) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException e) {
            log.error("[segment] 透明 PNG 解码失败", e);
            throw ErrorFactory.of(ResultCode.IMAGE_TOOL_FAILED);
        }
    }

    private byte[] composite(BufferedImage source, int[] mask, int maskSize, BackgroundColorEnum color) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] rgb = color.toRgb();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rowBuf = new int[width];

        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, rowBuf, 0, width);

            // 目标行在 320 蒙版上的纵向采样坐标（中心对齐，半像素偏移）
            float fy = (y + 0.5f) * maskSize / height - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, maskSize - 1);
            int y1 = Math.min(y0 + 1, maskSize - 1);
            float wy = fy - y0;

            for (int x = 0; x < width; x++) {
                float fx = (x + 0.5f) * maskSize / width - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, maskSize - 1);
                int x1 = Math.min(x0 + 1, maskSize - 1);
                float wx = fx - x0;

                // 蒙版值域是 0~255，必须先归一化到 0~1 才能当 alpha 用：
                // 直接拿 0~255 的值参与线性混合会使任何前景像素瞬间溢出钳位
                // （红底人像会糊成 00FFFF 这类纯色，人脸细节全丢）
                float alpha = bilerp(mask, maskSize, x0, y0, x1, y1, wx, wy) / 255f;
                // 原图自身可能带 alpha（PNG 透明背景），两者相乘才是最终不透明度
                int srcArgb = rowBuf[x];
                alpha *= ((srcArgb >>> 24) & 0xFF) / 255f;

                int sr = (srcArgb >> 16) & 0xFF;
                int sg = (srcArgb >> 8) & 0xFF;
                int sb = srcArgb & 0xFF;
                rowBuf[x] = ((clamp255(rgb[0] + (sr - rgb[0]) * alpha) & 0xFF) << 16)
                        | ((clamp255(rgb[1] + (sg - rgb[1]) * alpha) & 0xFF) << 8)
                        | (clamp255(rgb[2] + (sb - rgb[2]) * alpha) & 0xFF);
            }
            out.setRGB(0, y, width, 1, rowBuf, 0, width);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(out, "png", baos);
        } catch (IOException e) {
            log.error("[segment] 结果编码失败", e);
            throw ErrorFactory.of(ResultCode.IMAGE_TOOL_FAILED);
        }
        return baos.toByteArray();
    }

    /** 蒙版双线性插值：四角加权 */
    private float bilerp(int[] mask, int size, int x0, int y0, int x1, int y1, float wx, float wy) {
        float v00 = mask[y0 * size + x0];
        float v01 = mask[y0 * size + x1];
        float v10 = mask[y1 * size + x0];
        float v11 = mask[y1 * size + x1];
        float top = v00 + (v01 - v00) * wx;
        float bottom = v10 + (v11 - v10) * wx;
        return top + (bottom - top) * wy;
    }

    private int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** 混合结果可能是小数且可能因浮点误差越界，统一夹到 0~255 */
    private int clamp255(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}