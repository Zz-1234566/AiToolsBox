package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 人像分割（去背景换底色）结果。
 * <p>
 * 只承载<b>内存里的字节</b>，不负责落盘 —— 与 {@code CompressResult} 的边界一致：
 * 产物如何存储（COS / 本地）、要不要记历史，都是调用方（Controller / 工作流节点）的职责，
 * service 不越界碰存储层。
 * <p>
 * 宽高沿用<b>原图</b>尺寸而非模型输入尺寸（320×320）：蒙版放大回原图后按原分辨率输出，
 * 用户拿到的证件照才是原始清晰度。模型尺寸属于实现细节，不该泄漏给前端。
 */
@Data
@AllArgsConstructor
public class SegmentResult implements Serializable {

    /** 合成底色后的图片字节（PNG，带完整画面、不透明） */
    private byte[] imageBytes;

    /** 输出图片宽（原图宽） */
    private int width;

    /** 输出图片高（原图高） */
    private int height;

    /** MIME 类型，恒为 image/png */
    private String mimeType;
}