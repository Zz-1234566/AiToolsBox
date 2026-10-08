package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件压缩组件配置（图片 / 音频）。
 * <p>
 * 用于把超过第三方接口字节限制的文件，通过<b>有界迭代</b>压缩到限额以内。
 * 典型场景：腾讯云语音识别内联 base64 上限 5MB（原始音频约 3.7MB）。
 * <p>
 * <b>不做的事</b>：PDF / Word / txt 等文档不压缩 —— 项目内走
 * {@code DocumentParser} 本地抽取文字层后截断，不经第三方字节限制；
 * 且 PDF、docx 内部流已压缩，重写几乎压不动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "compress")
public class CompressConfig {

    /** 是否启用压缩组件（关闭时超限直接抛异常，不尝试压缩） */
    private Boolean enabled = true;

    /**
     * 最大迭代次数（每轮降一档质量/码率）。
     * 迭代用尽仍超限则抛业务异常，不静默返回原文件。
     */
    private Integer maxIterations = 5;

    // ─────────────── 图片分支参数 ───────────────
    // 迭代写法参考 GitHub 成熟实现（Stirling-PDF 93k★ / Tiny / EasyImageCompressor）：
    // while (未达标 && quality > 下界) { quality -= 步长; 重编码 }
    // 即「单调递减 + 明确下界」，结构上不可能死循环，不做递归。

    /** 初始 JPEG 质量（0~1，对应 IJG Q75 为「肉眼无损下限」，见 IJG FAQ） */
    private Float imageInitialQuality = 0.75f;

    /** 每轮质量步长（0~1 标尺，-0.05 等价于 IJG -5） */
    private Float imageQualityStep = 0.05f;

    /** 质量下限（低于此值画面严重失真，IJG FAQ 指出 Q10 以下接近「op art」） */
    private Float imageMinQuality = 0.1f;

    /** 初始最大宽度（像素），仅等比缩小，不放大 */
    private Integer imageInitialMaxWidth = 1600;

    /** 宽度每轮衰减系数（图片需双维度收敛：降质 + 缩放） */
    private Float imageWidthDecay = 0.8f;

    /** 宽度下限（像素） */
    private Integer imageMinWidth = 600;

    // ─────────────── 音频分支参数（ffmpeg 降码率） ───────────────

    /**
     * 逐轮尝试的音频码率（kbps），按数组顺序降档。
     * 语音识别场景 16k~64kbps 已足够，末档仍超限说明文件极长。
     */
    private String[] audioBitrates = {"64k", "48k", "32k", "24k", "16k"};

    /** 音频转码采样率（Hz）—— 与 TranscribeService 保持一致 */
    private Integer audioSampleRate = 16000;

    /** 音频声道数（1 = 单声道） */
    private Integer audioChannels = 1;
}
