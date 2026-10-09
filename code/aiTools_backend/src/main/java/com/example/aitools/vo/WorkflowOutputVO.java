package com.example.aitools.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流产物（对外出参）。
 * <p>
 * 前端按 {@code outputType} 分支渲染：
 * 1文本直接渲染 content，2图片给预览+下载，3视频给播放器，4音频给播放器，5文件给下载。
 */
@Data
public class WorkflowOutputVO implements Serializable {

    /** 产物 ID（前端点击下载/预览时带上，便于后端按需重签 COS 签名 URL） */
    private Long id;

    /** 节点 ID，如 n1 */
    private String nodeId;

    /** 节点名称快照 */
    private String nodeName;

    /** 产出该产物的工具编码 */
    private String toolCode;

    /** 对应第几个输入（多文件并行时区分） */
    private Integer fileIndex;

    /** 产物类型：1文本 2图片 3视频 4音频 5文件 */
    private Integer outputType;

    /** 文本内容（outputType=1 时有值） */
    private String content;

    /** 文件名（outputType!=1 时有值） */
    private String fileName;

    /** 文件访问地址（outputType!=1 时有值） */
    private String fileUrl;

    /** 文件字节数（outputType!=1 时有值） */
    private Long fileSize;

    /** 人类可读的体积，如 1.2 MB */
    private String fileSizeText;

    /** MIME 类型 */
    private String mimeType;

    /** 图片/视频宽度 */
    private Integer width;

    /** 图片/视频高度 */
    private Integer height;

    /** 音视频时长（毫秒） */
    private Integer durationMs;

    /** 创建时间 */
    private String createTime;
}
