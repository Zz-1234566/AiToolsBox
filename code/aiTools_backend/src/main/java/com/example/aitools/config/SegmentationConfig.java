package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 人像分割（图片去背景）配置。
 * <p>
 * 同时服务 {@code id-photo-bg-change} 证件照换背景色与 {@code image-bg-remover} 图片背景去除
 * 两个工具：二者共用同一个 ONNX 模型，推理引擎为本地 ONNX Runtime，纯 CPU 离线运行，
 * 不依赖任何第三方云服务。
 * <p>
 * <b>当前模型</b>：MODNet（modnet_photographic_portrait_matting.onnx，24.69MB，Apache-2.0）。
 * 预处理严格对齐 MODNet 官方写法（固定除以 255、mean/std 均为 0.5），
 * <b>不能替换成 U²-Net 系列</b>——后者用「整图 maxPix 做除数」的归一化，喂进来不会报错，
 * 只会静默输出错蒙版。
 * <p>
 * <b>modelPath 写法</b>：既支持 {@code classpath:models/x.onnx}（打进 jar 的默认模型），
 * 也支持普通文件路径 / {@code file:} 前缀（部署时换成外挂模型，便于热更模型不动包）。
 * 解析统一交给 Spring 的 {@code ResourceLoader}，业务代码只认 Resource。
 * <p>
 * <b>模型文件不入库</b>（{@code .gitignore} 排除 {@code *.onnx}），需自行下载，
 * 获取方式见 README「图片工具与本地 ONNX 模型」。
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
     * 默认读 classpath 下的 MODNet（随 jar 发布，开箱即用）；
     * 若部署环境希望模型外挂，改成绝对路径或 {@code file:/opt/models/modnet_photographic_portrait_matting.onnx} 即可。
     */
    private String modelPath = "classpath:models/modnet_photographic_portrait_matting.onnx";

    /**
     * 模型输入边长（当前 MODNet 导出为 512）。
     * <p>
     * 必须与所用模型的输入维度一致：推理输出 shape 会按此值强校验，不匹配即抛业务异常 ——
     * 与其让脏数据污染后续计算，不如在源头快速失败。
     */
    private Integer inputSize = 512;

    /**
     * 允许解码的最大像素总数（宽 × 高），默认 4000 万（约 4000×10000）。
     * <p>
     * 之所以在<b>解码前</b>用 ImageReader 只读尺寸就拦：一张 12000×8000 的图
     * 仅 ARGB 像素缓冲就要 1.4GB，多来几张直接把堆打爆，OOM 往往还没等得到业务异常。
     */
    private Long maxImagePixels = 40_000_000L;

    /**
     * 蒙版二值化阈值（0~1，对应 0~255 灰度）。
     * <p>
     * <b>默认 0.5</b>：低于此值的像素视为背景。调小（如 0.1）会把更多边缘像素判为前景，
     * 抠图更「干净」但容易带出背景杂色；调大（如 0.7）前景边缘更收缩，适合头发稀疏场景。
     */
    private Double maskThreshold = 0.5;

    /**
     * 蒙版边缘羽化像素数（0~3，0 表示不羽化）。
     * <p>
     * 模型输出蒙版的边缘是离散的「0 或 1」灰度，直接当 alpha 用会有肉眼可见的锯齿。
     * 用 3×3（或更大）均值卷积平滑一下，alpha 边缘就是连续的渐变，头发丝更自然。
     * <p>
     * <b>代价</b>：512×512 蒙版做 3×3 卷积 ≈ 236 万次循环，5×5 ≈ 655 万次；CPU 上几十毫秒可接受。
     */
    private Integer featherSize = 1;

    /**
     * 小图先放大到此最小边长，再缩放到模型输入尺寸。
     * <p>
     * 证件照 / 头像常常只有 300~600px，直接 resize 到模型输入尺寸后送到模型——
     * 信息损失严重，发丝级细节全丢。先把小图放大到此最小边长，再送模型输入尺寸，
     * 模型能看到的细节量显著提升，头发丝检出率明显变好。
     * <p>
     * 大图（maxSide > 此值）直接 resize，不做无意义放大。
     */
    private Integer minInputSize = 640;
}