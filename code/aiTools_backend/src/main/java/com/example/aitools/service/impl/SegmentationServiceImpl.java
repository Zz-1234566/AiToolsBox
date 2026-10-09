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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Iterator;

/**
 * 人像分割服务实现：本地 ONNX Runtime 加载 U²-Net 轻量版（u2netp），CPU 推理。
 * <p>
 * <b>算法口径严格对齐 rembg</b>（{@code base.py} + {@code u2net.py}），因为该组合已经过
 * 大规模验证，重新发明只会得到更差的抠图效果。三个最易踩错、且必须写死的点：
 * <ol>
 *   <li><b>归一化除数是「整图最大像素值」而非固定 255</b>。
 *       rembg 取 {@code maxPix} 后再除，能兼容 16bit / 少动态范围的图；
 *       写死 255 在深色证件照上蒙版会整体偏移，抠出来的人像发灰。</li>
 *   <li><b>推理取 outputs[0] 的第 0 个通道</b>。U²-Net 有 7 个输出（6 个侧边监督 + 1 个主输出），
 *       只认第 0 个；换 u2net_h 之类模型时此处形状会变，故强校验 shape。</li>
 *   <li><b>取张量值必须走 {@code getFloatBuffer()} 而非 {@code getValue()}**。
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

    /** ImageNet 均值（R/G/B），rembg base.py 的固定值 */
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};

    /** ImageNet 标准差（R/G/B），rembg base.py 的固定值 */
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    /** 全黑图兜底除数（rembg 里 maxPix 为 0 时的取值） */
    private static final float MAX_PIX_FALLBACK = 1e-6f;

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

        // 2) 预处理：缩放到模型输入尺寸 → 归一化 → NCHW
        int inputSize = segmentationConfig.getInputSize();
        BufferedImage resized = resize(source, inputSize, inputSize);
        float[] input = toInputTensor(resized, inputSize);

        // 3) 推理 → 0~255 灰度蒙版（模型尺寸）
        int[] mask = predictMask(input, inputSize);

        // 4) 蒙版双线性放大回原图尺寸 + 按 alpha 混合底色
        byte[] output = composite(source, mask, inputSize, color);

        return new SegmentResult(output, width, height, OUTPUT_MIME);
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
     * 把图片转成 NCHW 排布的 float 数组并按 ImageNet 统计量归一化。
     * <p>
     * 归一化公式 {@code (v / maxPix - mean[c]) / std[c]}，其中 {@code maxPix} 是
     * <b>整张图所有通道的最大像素值</b>（不是 255），rembg 用它做除数以适配
     * 动态范围不足的图；全 0 图用 1e-6 兜底防除零。
     *
     * @param image     已缩放到模型尺寸的图片
     * @param size      边长
     * @return 长度 {@code 3 * size * size} 的 float 数组，索引顺序 C=0,H=1,W=2
     */
    private float[] toInputTensor(BufferedImage image, int size) {
        int planeSize = size * size;
        float[] tensor = new float[3 * planeSize];

        // 第一遍只找最大值，不建中间数组
        int maxPix = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > maxPix) maxPix = r;
                if (g > maxPix) maxPix = g;
                if (b > maxPix) maxPix = b;
            }
        }
        float divisor = maxPix > 0 ? (float) maxPix : MAX_PIX_FALLBACK;

        // 第二遍按通道各写一张平面（NCHW）
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = image.getRGB(x, y);
                int idx = y * size + x;
                tensor[idx]                    = (((rgb >> 16) & 0xFF) / divisor - MEAN[0]) / STD[0];
                tensor[planeSize + idx]        = (((rgb >>  8) & 0xFF) / divisor - MEAN[1]) / STD[1];
                tensor[2 * planeSize + idx]    = ((rgb         & 0xFF) / divisor - MEAN[2]) / STD[2];
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