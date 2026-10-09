package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 人像分割（图片去背景）配置。
 * <p>
 * 对应 {@code id-photo-bg-change} 证件照换背景色工具，推理引擎为本地 ONNX Runtime
 * 加载 U²-Net 轻量版（u2netp.onnx），纯 CPU 离线运行，不依赖任何第三方云服务。
 * <p>
 * <b>modelPath 写法</b>：既支持 {@code classpath:models/u2netp.onnx}（打进 jar 的默认模型），
 * 也支持普通文件路径 / {@code file:} 前缀（部署时换成外挂模型，便于热更模型不动包）。
 * 解析统一交给 Spring 的 {@code ResourceLoader}，业务代码只认 Resource。
 * <p>
 * <b>inputSize 不可随意改</b>：u2netp.onnx 的输入/输出空间维度是固化的 320×320，
 * 改小会让输出 shape 校验直接失败；留此配置项只为了将来换 u2net / u2net_h 更大模型时
 * 不用改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "segmentation")
public class SegmentationConfig {

    /** 是否启用图片去背景能力（false 时工具不可用，避免未就绪的环境报错 500） */
    private Boolean enabled = true;

    /**
     * 模型文件位置。
     * <p>
     * 默认读 classpath 下的模型（随 jar 发布，开箱即用）；
     * 若部署环境希望模型外挂，改成绝对路径或 {@code file:/opt/models/u2netp.onnx} 即可。
     */
    private String modelPath = "classpath:models/u2netp.onnx";

    /**
     * 模型输入边长（u2netp 固定 320）。
     * <p>
     * 推理输出 shape 会按此值强校验，不匹配即抛业务异常 —— 与其让脏数据污染后续计算，
     * 不如在源头快速失败。
     */
    private Integer inputSize = 320;

    /**
     * 允许解码的最大像素总数（宽 × 高），默认 4000 万（约 4000×10000）。
     * <p>
     * 之所以在<b>解码前</b>用 ImageReader 只读尺寸就拦：一张 12000×8000 的图
     * 仅 ARGB 像素缓冲就要 1.4GB，多来几张直接把堆打爆，OOM 往往还没等得到业务异常。
     */
    private Long maxImagePixels = 40_000_000L;
}