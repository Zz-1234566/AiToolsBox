package com.example.aitools.common;

import java.util.Arrays;

/**
 * 分割（去背景）模型枚举。
 *
 * <p><b>为什么要有这个枚举</b>：换 ONNX 模型时最容易犯的错是
 * <b>只换 .onnx 文件、不换预处理口径</b>。这类错误<b>不会抛异常、不会报错</b>，
 * 只会静默输出错蒙版（README 里「不要用 u2netp 顶替」说的就是这件事）。
 * 所以把每个模型的规格（输入边长 / 归一化 mean·std / resize 口径 / 默认文件名）
 * <b>和模型放在一起</b>，让「换模型」变成一次配置切换，而不是一次手工对齐。
 *
 * <p><b>两个模型的预处理互不相同</b>（这是最容易踩的坑）：
 * <table border="1">
 *   <caption>各模型预处理口径对照</caption>
 *   <tr><th>模型</th><th>输入</th><th>mean</th><th>std</th><th>resize</th></tr>
 *   <tr><td>MODNet</td><td>512²</td><td>0.5/0.5/0.5</td><td>0.5/0.5/0.5</td><td>keep-ratio-pad(127中灰)</td></tr>
 *   <tr><td>RMBG-1.4</td><td>1024²</td><td>0.5/0.5/0.5</td><td><b>1.0/1.0/1.0</b></td><td>squash</td></tr>
 * </table>
 *
 * <p>来源：
 * <ul>
 *   <li>MODNet —— ZHKKKe/MODNet 官方 {@code src/models/modnet.py#preprocess}（保持长宽比 + pad 127）</li>
 *   <li>RMBG-1.4 —— {@code briaai/RMBG-1.4} 的 {@code preprocessor_config.json}（{@code normalize(im,[0.5]*3,[1.0]*3)}）</li>
 * </ul>
 */
public enum SegmentationModelEnum {

    /**
     * MODNet：人像 matting（Apache-2.0，24.7MB，512²）。
     * <p>只学过「一个人」的前景先验，遇到「小孩 + 画面外伸进来的成人手臂」这类构图会误判。
     */
    MODNET("modnet", "MODNet（人像 matting）", 512,
            new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f},
            ResizeMode.KEEP_RATIO_PAD, OutputMode.PROBABILITY, null,
            "modnet_photographic_portrait_matting.onnx", "Apache-2.0"),

    /**
     * BRIA RMBG-1.4：通用显著物分割（bria RAIL 许可，**仅限非商用**，168MB fp32 / 42MB int8，1024²）。
     * <p>比 MODNet 分辨率高 4 倍（1024² vs 512²），对发丝、衣褶等细结构更友好；
     * 实测 CPU 单张 1.3s / 常驻 908MB，是 MODNet 的 5 倍耗时、5 倍内存。
     * <p><b>商用需另行授权</b>；毕业设计等非商用场景可直接用。
     */
    RMBG_1_4("rmbg-1.4", "BRIA RMBG-1.4（通用显著物·仅非商用）", 1024,
            new float[]{0.5f, 0.5f, 0.5f}, new float[]{1.0f, 1.0f, 1.0f},
            ResizeMode.SQUASH, OutputMode.PROBABILITY, null,
            "rmbg_1_4.onnx", "bria RAIL（非商用）");

    /**
     * 输入图的 resize 口径。
     */
    public enum ResizeMode {
        /**
         * 保持长宽比缩放 + pad 成方形（pad 值 = 127 中灰）。
         * <p>MODNet 官方口径；竖版人像不会被压扁。
         */
        KEEP_RATIO_PAD,
        /**
         * 直接 resize 成方形（内容被拉伸）。
         * <p>RMBG-1.4 的官方口径（它训练时就是方形直接 resize）。
         */
        SQUASH
    }

    /**
     * 模型输出的语义（决定怎么把原始输出换算成 alpha 蒙版）。
     *
     * <p><b>这是换模型时最容易翻车的地方</b>：不同模型 ONNX 导出的输出语义<b>不一样</b>，
     * 实测：
     * <ul>
     *   <li>MODNet —— 输出已是 0~1 <b>概率</b>（模型内部带 sigmoid），实测值域 [0, 0.2]</li>
     *   <li>BiRefNet 系 —— 输出是<b>原始 logit</b>（无界），实测值域 [-14.4, +12.7]，
     *       负值占 72.9%、大于 1 的占 26.7%</li>
     * </ul>
     *
     * <p><b>搞错的后果</b>：对 logit 一律做 min-max 归一化，会把有意义的概率区间压成窄带 ——
     * 真实置信度 0.7% 的背景像素被抬到 <b>35% 不透明度</b>，
     * 抠出来是「半透明鬼影 + 躯干糊掉」，<b>看着像模型很差，其实是换算口径错了</b>。
     * <p>（实测 BiRefNet-lite 的输出值域是 [-14.4, +12.7]、负值占 72.9%，是典型的 logit；
     * 接新模型时**必须先实测输出值域**再定口径。）
     */
    public enum OutputMode {
        /**
         * 输出已是 0~1 概率：按整图 min-max 拉伸成 0~255 灰度蒙版。
         * <p>MODNet / RMBG-1.4 走这条（RMBG 官方代码本身就是对输出做 min-max）。
         */
        PROBABILITY,
        /**
         * 输出是 logit：先过 sigmoid 得到概率，<b>不再做 min-max</b>
         * （sigmoid 本身就是标定过的概率，min-max 反而会毁掉它）。
         * <p>当前两个模型都是 PROBABILITY —— 留着这个分支是给以后接新模型用的。
         */
        LOGIT
    }

    /** 配置值（yml 里的写法） */
    private final String code;
    /** 展示名（调试页 / 日志用） */
    private final String displayName;
    /** 模型输入边长（模型导出时的方形尺寸） */
    private final int inputSize;
    /** 归一化均值（R/G/B） */
    private final float[] mean;
    /** 归一化标准差（R/G/B） */
    private final float[] std;
    /** 官方 resize 口径 */
    private final ResizeMode resizeMode;
    /** 输出语义（概率 / logit） */
    private final OutputMode outputMode;
    /**
     * 该模型推荐的蒙版二值化阈值（null = 跟随 yml 配置）。
     * <p>
     * <b>什么时候需要它</b>：<b>二值分割</b>（Dichotomous）类模型在 sigmoid 之后
     * 要按 0.5 切一刀才能得到干净的前景；不切的话，背景像素那点低置信度的 alpha
     * 会连成一层<b>淡色鬼影</b>。
     * <p>matting 类模型（MODNet）反而要保留软边（阈值 0，不二值化）。
     */
    private final Double defaultMaskThreshold;
    /** 默认模型文件名（放在 classpath:models/ 下） */
    private final String defaultFileName;
    /** 模型许可（商用前必须确认） */
    private final String license;

    SegmentationModelEnum(String code, String displayName, int inputSize,
                           float[] mean, float[] std,
                           ResizeMode resizeMode, OutputMode outputMode, Double defaultMaskThreshold,
                           String defaultFileName, String license) {
        this.code = code;
        this.displayName = displayName;
        this.inputSize = inputSize;
        this.mean = mean;
        this.std = std;
        this.resizeMode = resizeMode;
        this.outputMode = outputMode;
        this.defaultMaskThreshold = defaultMaskThreshold;
        this.defaultFileName = defaultFileName;
        this.license = license;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getInputSize() {
        return inputSize;
    }

    /** 归一化均值（防御性拷贝） */
    public float[] getMean() {
        return Arrays.copyOf(mean, mean.length);
    }

    /** 归一化标准差（防御性拷贝） */
    public float[] getStd() {
        return Arrays.copyOf(std, std.length);
    }

    public ResizeMode getResizeMode() {
        return resizeMode;
    }

    /** 输出语义（概率 / logit） */
    public OutputMode getOutputMode() {
        return outputMode;
    }

    /** 该模型推荐的蒙版二值化阈值（null = 跟随 yml 配置） */
    public Double getDefaultMaskThreshold() {
        return defaultMaskThreshold;
    }

    public String getDefaultFileName() {
        return defaultFileName;
    }

    public String getLicense() {
        return license;
    }

    /** 默认模型路径（classpath:models/&lt;默认文件名&gt;） */
    public String getDefaultModelPath() {
        return "classpath:models/" + defaultFileName;
    }

    /**
     * 按本模型的归一化口径，把一个通道的像素值（0~255）转成模型输入值。
     *
     * <p>公式：{@code (v / 255 - mean[c]) / std[c]}。
     *
     * @param value255 像素值（0~255）
     * @param channel  通道下标（0=R, 1=G, 2=B）
     */
    public float normalize(int value255, int channel) {
        return ((value255 / 255.0f) - mean[channel]) / std[channel];
    }

    /**
     * 按配置值解析模型（大小写与首尾空白不敏感）。
     *
     * @param code 配置值，如 {@code modnet} / {@code rmbg-1.4}
     * @return 对应的枚举；<b>无法识别时返回 null</b>（回退策略由调用方决定，不在这里抛异常）
     */
    public static SegmentationModelEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (SegmentationModelEnum m : values()) {
            if (m.code.equalsIgnoreCase(trimmed)) {
                return m;
            }
        }
        return null;
    }
}
