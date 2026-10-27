package com.example.aitools.service.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.aitools.common.BackgroundColorEnum;
import com.example.aitools.common.ResultCode;
import com.example.aitools.common.SegmentationModelEnum;
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
import java.awt.Color;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.example.aitools.common.MimeTypes;

/**
 * 人像分割服务实现：本地 ONNX Runtime 加载分割模型，CPU 推理。
 * <p>
 * <b>模型可插拔</b>：具体用哪个模型由 {@code segmentation.model} 决定，
 * 规格（输入边长 / 归一化 mean·std / resize 口径）统一由
 * {@link SegmentationModelEnum} 提供，<b>换模型不需要改这里的任何一行</b>。
 * <p>
 * <b>各模型预处理口径互不相同，这是本类最容易踩的坑</b>：
 * <ul>
 *   <li>MODNet（{@code mean/std=0.5/0.5/0.5} + 保持比例 pad 127 中灰）</li>
 *   <li>RMBG-1.4（{@code mean=0.5, std=1.0} + 直接 resize 成方形）</li>
 * </ul>
 * 口径对不上时<b>不会抛异常</b>，只会静默输出错蒙版，所以归一化公式统一走
 * {@link SegmentationModelEnum#normalize(int, int)}，不允许在业务代码里手写。
 * <p>
 * 另两个历史踩坑点（MODNet 口径下）：
 * <ol>
 *   <li><b>归一化除数是「固定 255」而非整图 maxPix</b>。MODNet 官方就是固定 255；写 {@code maxPix}
 *       （rembg/u2netp 习惯）会让蒙版整体偏移，头发丝检测率显著下降。</li>
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
     * 各模型的会话缓存（懒加载 + 双检锁，key = 模型）。
     * <p>
     * <b>为什么按模型缓存</b>：调试页要能同时跑多个模型做 A/B 对照，
     * 不能只有一个「当前模型」的 session。
     * <p>
     * <b>为什么必须缓存</b>：新建一次 {@code OrtSession} 要把模型读进来并建图，
     * 实测约 2 秒；而推理本身只要 146ms。若每次请求都新建 session，
     * 一次请求的开销会有 93% 花在加载模型上，且并发时内存里同时躺着 N 份模型。
     * <p>
     * <b>为什么懒加载</b>：模型加载要 2 秒，如果放在构造期会拖慢整个应用启动，
     * 且在 {@code segmentation.enabled=false} 的环境里属于无谓开销。
     */
    private final Map<SegmentationModelEnum, ModelSession> sessions = new ConcurrentHashMap<>();

    /**
     * 模型会话：{@code OrtSession} + 该模型的输入节点名。
     * <p>
     * 输入节点名动态取，不硬编码：实测 MODNet 叫 {@code input.1}、
 * 但别的模型可能叫别的（ONNX 图里的节点名随导出工具/版本变化），
     * 写死会在换模型时静默失效。
     */
    private static final class ModelSession {
        final OrtSession session;
        final String inputName;

        ModelSession(OrtSession session, String inputName) {
            this.session = session;
            this.inputName = inputName;
        }
    }

    private static final String OUTPUT_MIME = MimeTypes.PNG;

    /**
     * 对比页预览图的长边上限（像素）。
     * <p>
     * 原图 4000×3000 的透明 PNG 编码后有好几 MB，base64 再涨 1/3，
     * 一个模型一条响应就撑爆 JSON。对比页只需要并排看效果，1000px 足够看发丝与边缘。
     */
    private static final int COMPARE_PREVIEW_MAX_SIDE = 1000;

    @Override
    public SegmentResult removeBackground(byte[] imageBytes, String backgroundColor) {
        return removeBackground(imageBytes, backgroundColor, null);
    }

    /**
     * 去背景并合成底色，可显式指定分割模型（工具页的模型选择器用）。
     *
     * @param model 分割模型；null = 用配置里的默认模型
     */
    private SegmentResult removeBackground(byte[] imageBytes, String backgroundColor,
                                           SegmentationModelEnum model) {
        // 1) 前置校验：总开关 + 入参
        if (!Boolean.TRUE.equals(segmentationConfig.getEnabled())) {
            throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请先选择要处理的证件照");
        }
        // 底色在入口就解析：非法值早失败，避免白白跑完推理再报错
        BackgroundColorEnum color = BackgroundColorEnum.fromCode(backgroundColor);

        // 2) 分割：显式指定模型时用该模型自己的规格，否则用配置解析出来的
        SegmentationModelEnum useModel = model != null ? model : segmentationConfig.resolveModel();
        int inputSize = model != null ? model.getInputSize() : segmentationConfig.resolveInputSize();
        SegmentationModelEnum.ResizeMode resizeMode = model != null
                ? model.getResizeMode() : segmentationConfig.resolveResizeMode();
        MaskResult masked = segment(imageBytes, useModel, inputSize, resizeMode);

        // 3) 按蒙版 alpha 混合底色
        byte[] output = composite(masked.source, masked.mask, masked.maskW, masked.maskH, color);

        return new SegmentResult(output, masked.width, masked.height, OUTPUT_MIME);
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

        // 2) 分割（用配置里的模型）
        SegmentationModelEnum model = segmentationConfig.resolveModel();
        MaskResult masked = segment(imageBytes, model,
                segmentationConfig.resolveInputSize(), segmentationConfig.resolveResizeMode());

        // 3) 透明合成
        byte[] output = compositeTransparent(masked.source, masked.mask, masked.maskW, masked.maskH);

        return new SegmentResult(output, masked.width, masked.height, OUTPUT_MIME);
    }

    @Override
    public SegmentResult removeBackgroundTransparent(byte[] imageBytes, SegmentationModelEnum model) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请先选择要处理的图片");
        }
        if (model == null) {
            throw ErrorFactory.of(ResultCode.PARAM_ERROR, "未指定分割模型");
        }

        // 显式指定模型时，输入边长与 resize 口径一律用**该模型自己的**官方规格，
        // 不受配置里的 input-size / preprocess-mode 影响（那是给「默认模型」做覆盖用的）。
        MaskResult masked = segment(imageBytes, model, model.getInputSize(), model.getResizeMode());
        byte[] output = compositeTransparent(masked.source, masked.mask, masked.maskW, masked.maskH);

        return new SegmentResult(output, masked.width, masked.height, OUTPUT_MIME);
    }

    /**
     * 分割核心：解码 → 小图放大 → 预处理 → 归一化 → 推理 → 蒙版精修 → 裁掉 pad 区。
     * <p>
     * 三种去/换背景模式的差异只在<b>最后的合成</b>，前面的分割流程完全共用。
     *
     * @param imageBytes  原始图片字节
     * @param model       分割模型（决定归一化口径与会话）
     * @param inputSize   模型输入边长
     * @param resizeMode  resize 口径（保持比例 pad / 直接压成方形）
     */
    private MaskResult segment(byte[] imageBytes, SegmentationModelEnum model,
                               int inputSize, SegmentationModelEnum.ResizeMode resizeMode) {
        BufferedImage source = decodeWithPixelLimit(imageBytes);
        int width = source.getWidth();
        int height = source.getHeight();

        // 小图先放大（不动大图）：300px 头像放大到 640 再送模型，模型看到的细节翻 4 倍
        BufferedImage upscaled = upscaleIfTooSmall(source);

        // 预处理：按模型口径（保持长宽比 + pad 成方形 / 直接压成方形）→ 归一化 → NCHW
        PaddedInput padded = preprocessForModel(upscaled, inputSize, resizeMode);
        float[] input = toInputTensor(padded.image, inputSize, model);

        // 推理 → 0~255 灰度蒙版（模型尺寸）
        int[] mask = predictMask(input, inputSize, model);

        // 蒙版二值化 + 羽化：让 alpha 边缘平滑（头发丝自然）。
        // 阈值优先级：模型推荐值 > yml 配置。
        // MODNet / RMBG 都用 yml 的 0（不二值化，保留半透明）。
        double threshold = model.getDefaultMaskThreshold() != null
                ? model.getDefaultMaskThreshold()
                : segmentationConfig.getMaskThreshold();
        int[] maskRefined = refineMask(mask, inputSize, threshold, segmentationConfig.getFeatherSize());

        // 裁掉 pad 区（得到与原图同长宽比的蒙版）
        int[] valid = cropValidRegion(maskRefined, inputSize, padded);

        return new MaskResult(source, valid, padded.w, padded.h, width, height);
    }

    /**
     * 分割核心的中间产物：原图 + 裁掉 pad 区后的蒙版。
     */
    private static final class MaskResult {
        final BufferedImage source;
        final int[] mask;
        final int maskW;
        final int maskH;
        final int width;
        final int height;

        MaskResult(BufferedImage source, int[] mask, int maskW, int maskH, int width, int height) {
            this.source = source;
            this.mask = mask;
            this.maskW = maskW;
            this.maskH = maskH;
            this.width = width;
            this.height = height;
        }
    }

    @Override
    public SegmentResult changeBackground(byte[] foregroundImage, byte[] backgroundImage) {
        return changeBackground(foregroundImage, backgroundImage, null);
    }

    /**
     * 换背景图，可显式指定分割模型（工具页的模型选择器用）。
     *
     * @param model 分割模型；null = 用配置里的默认模型
     */
    private SegmentResult changeBackground(byte[] foregroundImage, byte[] backgroundImage,
                                           SegmentationModelEnum model) {
        if (foregroundImage == null || foregroundImage.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请上传要处理的图片");
        }
        if (backgroundImage == null || backgroundImage.length == 0) {
            throw ErrorFactory.of(ResultCode.PARAM_MISSING, "请上传背景图");
        }

        // 1) 前景去背景 → 透明 PNG
        SegmentResult fg = model != null
                ? removeBackgroundTransparent(foregroundImage, model)
                : removeBackgroundTransparent(foregroundImage);
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
        return new SegmentResult(baos.toByteArray(), W, H, MimeTypes.JPEG);
    }

    @Override
    public SegmentResult changeBackground(byte[] foregroundImage, String backgroundColor) {
        // 纯色合成的快捷入口：输出 TYPE_INT_RGB PNG
        return removeBackground(foregroundImage, backgroundColor);
    }

    @Override
    public SegmentResult processImage(byte[] imageBytes, String mode,
                                      String backgroundColor, byte[] backgroundImage) {
        return processImage(imageBytes, mode, backgroundColor, backgroundImage, null);
    }

    @Override
    public SegmentResult processImage(byte[] imageBytes, String mode,
                                      String backgroundColor, byte[] backgroundImage,
                                      SegmentationModelEnum model) {
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
                return model != null
                        ? removeBackgroundTransparent(imageBytes, model)
                        : removeBackgroundTransparent(imageBytes);
            case "color":
                if (backgroundColor == null || backgroundColor.isBlank()) {
                    throw ErrorFactory.of(ResultCode.PARAM_MISSING, "color 模式必须指定底色（red/blue/white）");
                }
                return removeBackground(imageBytes, backgroundColor, model);
            case "image":
                if (backgroundImage == null || backgroundImage.length == 0) {
                    throw ErrorFactory.of(ResultCode.PARAM_MISSING, "image 模式必须上传背景图");
                }
                // 走「透明 PNG + 叠到 bg」路径
                return changeBackground(imageBytes, backgroundImage, model);
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
     * 按 **MODNet 官方口径**预处理：<b>保持长宽比</b>缩放 + <b>pad 成方形</b>。
     *
     * <p>官方 {@code src/models/modnet.py#preprocess}：
     * <pre>
     *   ratio = image_size[0] / max(h, w)     // 保持长宽比，长边缩到 512
     *   im = F.interpolate(im, size=(h*ratio, w*ratio))
     *   im = pad_divide_by(im, divisor)        // pad 到 32 的倍数，pad 值 = 127（中灰）
     * </pre>
     *
     * <p><b>为什么要改</b>：原实现是 <b>squash</b>（直接 resize 成 size×size），
     * 竖版人像会被<b>压扁</b>，模型看到的是**变形的人**（训练分布之外），
     * 检出质量明显下降 —— 细长结构（手臂、头发丝）最先断。
     * 官方是「保持比例 + pad」，不是压扁。
     *
     * <p><b>为什么必须 pad 而不是直接送非方形</b>：本模型内部有硬编码的方形特征图
     * （非方形输入会在 Concat 节点维度不匹配而报错，实测 512×169 直接失败）。
     * 所以「保持比例」只能靠 pad 实现。
     *
     * @param src  原图
     * @param size 方形边长（= 模型输入边长）
     * @return 预处理结果：方形输入图 + 原图内容在方形里的有效区域
     */
    private PaddedInput resizeKeepRatioPad(BufferedImage src, int size) {
        int w = src.getWidth();
        int h = src.getHeight();
        // 保持长宽比：长边缩到 size
        double ratio = (double) size / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));

        BufferedImage scaled = drawScaled(src, nw, nh, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // pad 到方形：pad 值用 127（中灰），与官方 pad_divide_by 一致
        BufferedImage padded = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = padded.createGraphics();
        // 有效区域（居中放置，与官方一致）
        int ox = (size - nw) / 2;
        int oy = (size - nh) / 2;
        try {
            g.setColor(new Color(127, 127, 127));
            g.fillRect(0, 0, size, size);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // 内容必须画在 (ox, oy)——与下面返回的有效区域严格一致。
            // 画在 (0,0) 而按居中裁蒙版，会让非方形图的蒙版整体错位（原实现就是这个 bug）。
            g.drawImage(scaled, ox, oy, null);
        } finally {
            g.dispose();
        }
        return new PaddedInput(padded, ox, oy, nw, nh);
    }

    /** 预处理结果：方形输入图 + 有效区域（ox, oy, w, h） */
    private static final class PaddedInput {
        final BufferedImage image;
        final int ox;
        final int oy;
        final int w;
        final int h;

        PaddedInput(BufferedImage image, int ox, int oy, int w, int h) {
            this.image = image;
            this.ox = ox;
            this.oy = oy;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * 预处理统一入口：按<b>模型官方口径</b>选择
     * 「保持比例 + pad」（MODNet）或「压扁成方形」（RMBG-1.4）。
     *
     * @param src        原图
     * @param size       方形边长（= 模型输入边长）
     * @param resizeMode resize 口径
     * @return 方形输入图 + 有效区域（两种模式下都有效：squash 时有效区域 = 整个方形）
     */
    private PaddedInput preprocessForModel(BufferedImage src, int size,
                                           SegmentationModelEnum.ResizeMode resizeMode) {
        if (resizeMode == SegmentationModelEnum.ResizeMode.SQUASH) {
            // squash：整张方形都是有效内容
            return new PaddedInput(resize(src, size, size), 0, 0, size, size);
        }
        return resizeKeepRatioPad(src, size);
    }

    /**
     * 从方形蒙版里裁出有效区域（去掉 pad），得到与原图**同长宽比**的蒙版。
     * <p>裁出来的尺寸不再是方形 —— 后续合成要按 (maskW, maskH) 双线性采样。
     */
    private int[] cropValidRegion(int[] mask, int size, PaddedInput p) {
        int[] out = new int[p.w * p.h];
        for (int y = 0; y < p.h; y++) {
            System.arraycopy(mask, (p.oy + y) * size + p.ox, out, y * p.w, p.w);
        }
        return out;
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
     * 把图片转成 NCHW 排布的 float 数组，并按<b>模型自己的</b>归一化口径处理。
     * <p>
     * 公式 {@code (v / 255 - mean[c]) / std[c]}，mean/std 由
     * {@link SegmentationModelEnum#normalize(int, int)} 提供：
     * <ul>
     *   <li>MODNet —— {@code mean=std=0.5}，像素压到 {@code [-1,1]}（官方推荐写法）</li>
     *   <li>RMBG-1.4 —— {@code mean=0.5, std=1.0}</li>
     * </ul>
     * 与 rembg/u2netp 的「整图 maxPix 做除数」不同——后者是给 U²-Net 用的，不能套到这些模型上。
     *
     * @param image 已缩放到模型尺寸的图片
     * @param size  边长
     * @param model 分割模型（决定归一化口径）
     * @return 长度 {@code 3 * size * size} 的 float 数组，索引顺序 C=0,H=1,W=2
     */
    private float[] toInputTensor(BufferedImage image, int size, SegmentationModelEnum model) {
        int planeSize = size * size;
        float[] tensor = new float[3 * planeSize];

        // 按通道各写一张平面（NCHW）
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = image.getRGB(x, y);
                int idx = y * size + x;
                tensor[idx]                 = model.normalize((rgb >> 16) & 0xFF, 0);
                tensor[planeSize + idx]     = model.normalize((rgb >> 8) & 0xFF, 1);
                tensor[2 * planeSize + idx] = model.normalize(rgb & 0xFF, 2);
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
     * @param model     分割模型（决定用哪个会话）
     * @return 灰度蒙版（一维数组，行优先）
     */
    private int[] predictMask(float[] input, int inputSize, SegmentationModelEnum model) {
        ModelSession modelSession = getModelSession(model);
        OrtSession ortSession = modelSession.session;
        long[] shape = {1, 3, inputSize, inputSize};
        try (OnnxTensor tensor = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(input), shape);
             OrtSession.Result result = ortSession.run(java.util.Map.of(modelSession.inputName, tensor))) {

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
            float[] raw = new float[planeSize];
            for (int i = 0; i < planeSize; i++) {
                raw[i] = buffer.get(i);
            }

            // 按**模型自己的输出语义**换算：
            // MODNet / RMBG 输出的是概率（min-max 拉伸）；
            // 若接的模型输出的是 logit，则要先 sigmoid（见 SegmentationModelEnum.OutputMode）。
            // 混用会让抠图变成半透明鬼影，详见 SegmentationModelEnum.OutputMode 的注释。
            return rawOutputToMask(raw, model.getOutputMode());
        } catch (OrtException e) {
            log.error("[segment] ONNX 推理失败", e);
            throw ErrorFactory.of(ResultCode.SEGMENT_INFER_FAILED);
        }
    }

    /**
     * 把模型原始输出换算成 0~255 灰度蒙版（包级可见，供单元测试锁住口径）。
     *
     * @param raw  模型输出（{@code PROBABILITY} 口径下是 0~1 概率；{@code LOGIT} 口径下是原始 logit）
     * @param mode 输出语义
     * @return 灰度蒙版（行优先，长度 = raw.length）
     */
    static int[] rawOutputToMask(float[] raw, SegmentationModelEnum.OutputMode mode) {
        int n = raw.length;
        int[] mask = new int[n];

        if (mode == SegmentationModelEnum.OutputMode.LOGIT) {
            // logit → sigmoid → 概率，**不再做 min-max**：
            // sigmoid 本身就是标定过的概率；再 min-max 会把有效区间压成窄带，
            // 真实置信度 0.7% 的背景会被抬到 35% 不透明度 → 抠出来稀碎。
            for (int i = 0; i < n; i++) {
                float p = 1f / (1f + (float) Math.exp(-raw[i]));
                mask[i] = Math.round(Math.min(1f, Math.max(0f, p)) * 255f);
            }
            return mask;
        }

        // PROBABILITY：按整图 min/max 拉伸（MODNet / RMBG 既有行为，保持不变）
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float v : raw) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float range = max - min;
        if (range <= 0f) {
            // 全图同一值（纯色图 / 模型无输出）：没有前景可言，整图判为背景
            log.warn("[segment] 模型输出为常量值，按全背景处理");
            return mask;
        }
        for (int i = 0; i < n; i++) {
            float norm = (raw[i] - min) / range;
            mask[i] = Math.round(Math.min(1f, Math.max(0f, norm)) * 255f);
        }
        return mask;
    }

    /**
     * 取（懒加载）指定模型的会话。
     *
     * @param model 分割模型
     * @return 已就绪的会话（含输入节点名）
     */
    private ModelSession getModelSession(SegmentationModelEnum model) {
        ModelSession local = sessions.get(model);
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = sessions.get(model);
            if (local == null) {
                OrtSession created = createSession(model);
                // 输入节点名动态取，不硬编码：实测 MODNet 叫 "input.1"、
                // 不同模型的节点名不一样（ONNX 图里的名字随导出工具/版本变化），
                // 写死会在换模型时静默失效
                String name = created.getInputNames().iterator().next();
                local = new ModelSession(created, name);
                sessions.put(model, local);
                log.info("[segment] ONNX 模型加载完成 model={} input={} outputs={}",
                        model.getCode(), name, created.getOutputNames());
            }
            return local;
        }
    }

    /**
     * 创建 {@code OrtSession}。
     * <p>
     * classpath 资源（模型打进 jar）走 {@code byte[]} 构造器，
     * 因为 {@code createSession(String)} 只接受真实文件路径，jar 内模型没有独立路径；
     * 外挂文件模型走路径构造器，让 Runtime 自己做内存映射、少占一份堆。
     */
    private OrtSession createSession(SegmentationModelEnum model) {
        String modelPath = segmentationConfig.resolveModelPath(model);
        try {
            Resource resource = new DefaultResourceLoader().getResource(modelPath);
            if (!resource.exists()) {
                log.error("[segment] 模型文件不存在 path={} model={}", modelPath, model.getCode());
                throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE,
                        "分割模型文件不存在（" + model.getCode() + "），请检查 models 目录");
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
            log.error("[segment] ONNX 模型加载失败 path={} model={}", modelPath, model.getCode(), e);
            throw ErrorFactory.of(ResultCode.SEGMENT_MODEL_UNAVAILABLE,
                    "分割模型加载失败（" + model.getCode() + "）");
        }
    }

    /** 应用关闭时释放模型内存，避免重启期间内存被旧进程占着 */
    @PreDestroy
    public void destroy() {
        for (Map.Entry<SegmentationModelEnum, ModelSession> entry : sessions.entrySet()) {
            try {
                entry.getValue().session.close();
            } catch (OrtException e) {
                log.warn("[segment] 关闭 ONNX session 失败 model={}", entry.getKey().getCode(), e);
            }
        }
        sessions.clear();
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
    private byte[] compositeTransparent(BufferedImage source, int[] mask, int maskW, int maskH) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rowBuf = new int[width];

        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, rowBuf, 0, width);

            float fy = (y + 0.5f) * maskH / height - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, maskH - 1);
            int y1 = Math.min(y0 + 1, maskH - 1);
            float wy = fy - y0;

            for (int x = 0; x < width; x++) {
                float fx = (x + 0.5f) * maskW / width - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, maskW - 1);
                int x1 = Math.min(x0 + 1, maskW - 1);
                float wx = fx - x0;

                float alpha = bilerp(mask, maskW, maskH, x0, y0, x1, y1, wx, wy) / 255f;
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

    private byte[] composite(BufferedImage source, int[] mask, int maskW, int maskH, BackgroundColorEnum color) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] rgb = color.toRgb();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rowBuf = new int[width];

        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, rowBuf, 0, width);

            // 目标行在 320 蒙版上的纵向采样坐标（中心对齐，半像素偏移）
            float fy = (y + 0.5f) * maskH / height - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, maskH - 1);
            int y1 = Math.min(y0 + 1, maskH - 1);
            float wy = fy - y0;

            for (int x = 0; x < width; x++) {
                float fx = (x + 0.5f) * maskW / width - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, maskW - 1);
                int x1 = Math.min(x0 + 1, maskW - 1);
                float wx = fx - x0;

                // 蒙版值域是 0~255，必须先归一化到 0~1 才能当 alpha 用：
                // 直接拿 0~255 的值参与线性混合会使任何前景像素瞬间溢出钳位
                // （红底人像会糊成 00FFFF 这类纯色，人脸细节全丢）
                float alpha = bilerp(mask, maskW, maskH, x0, y0, x1, y1, wx, wy) / 255f;
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
    private float bilerp(int[] mask, int w, int h, int x0, int y0, int x1, int y1, float wx, float wy) {
        float v00 = mask[y0 * w + x0];
        float v01 = mask[y0 * w + x1];
        float v10 = mask[y1 * w + x0];
        float v11 = mask[y1 * w + x1];
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