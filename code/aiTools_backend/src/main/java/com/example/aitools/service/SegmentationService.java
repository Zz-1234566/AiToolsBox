package com.example.aitools.service;

import com.example.aitools.dto.SegmentResult;

/**
 * 人像分割（图片去背景）服务。
 * <p>
 * 服务于 {@code id-photo-bg-change} 证件照换背景色工具：输入一张人像照片，
 * 本地 ONNX 推理出前景蒙版，再把背景抠掉并合成指定底色。
 * <p>
 * <b>为什么是本地推理而不是云 API</b>：证件照属于个人敏感信息，出境/上传到第三方
 * 存在合规风险；且 U²-Net 轻量版（u2netp，4.4MB）在 CPU 上单张 146ms，完全够用，
 * 离线部署反而是本项目的加分项。
 * <p>
 * <b>职责边界</b>：本服务只负责「字节进、字节出」，不碰存储、不落库、不记历史，
 * 产物如何入库由调用方决定（Controller 走 {@link ToolOutputService#saveFileOutput}）。
 */
public interface SegmentationService {

    /**
     * 去背景并合成指定底色。
     * <p>
     * 流程：解码 → 缩放到模型输入尺寸 → 归一化送 ONNX 推理 → 取第 0 个输出的首通道
     * 做 min-max 归一化得到 0~255 蒙版 → 双线性放大回原图尺寸 → 按蒙版 alpha 混合底色。
     * 输出保持<b>原图分辨率</b>，不降到模型的 320×320。
     *
     * @param imageBytes     原始图片字节（png / jpg / bmp 等 ImageIO 能解的格式）
     * @param backgroundColor 底色编码：red / blue / white（大小写不敏感），
     *                       为 null 或空白时默认红色
     * @return 合成后的图片（PNG，恒不透明）+ 宽高信息
     * @throws com.example.aitools.exception.BusinessException 能力未启用、模型不可用、
     *                                                    底色非法、图片无法解码、
     *                                                    图片像素数超限、推理失败
     */
    SegmentResult removeBackground(byte[] imageBytes, String backgroundColor);

    /**
     * 去背景，输出<b>透明 PNG（TYPE_INT_ARGB）</b>，不做底色合成。
     * <p>
     * 给 {@code image-bg-changer}（fg + 用户上传背景图）合成路径使用。
     *
     * @param imageBytes 原始图片字节
     * @return 透明 PNG 字节 + 宽高信息
     */
    SegmentResult removeBackgroundTransparent(byte[] imageBytes);

    /**
     * 换背景图：fg 去背景（透明 PNG）+ 叠到用户上传的 bg 之上，输出 JPG。
     * <p>
     * 复合尺寸取 fg / bg 宽高的较大值；fg 透明区域透出 bg。
     *
     * @param foregroundImage 人像前景（任意格式）
     * @param backgroundImage 背景图（任意格式）
     * @return JPG 字节 + 宽高
     */
    SegmentResult changeBackground(byte[] foregroundImage, byte[] backgroundImage);

    /**
     * 换背景色：直接复用 {@link #removeBackground(byte[], String)}，是 changeBackground 的纯色快捷版。
     */
    SegmentResult changeBackground(byte[] foregroundImage, String backgroundColor);
}