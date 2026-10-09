package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流产物（sys_workflow_output）。
 * <p>
 * 与 {@code sys_workflow_run.node_results.outputs} 的关系：
 * <ul>
 *   <li>文本类产物仍留在 {@code node_results}（前端直接渲染）；</li>
 *   <li>文件类产物（图片/视频/音频/文件）存本表，实体在 COS，表里只存 URL 与元信息。</li>
 * </ul>
 * 定位维度：{@code runId + nodeId + fileIndex}，与 {@code node_results[nid].outputs}
 * 的下标一一对应（N 个输入 → N 个输出）。
 */
@Data
@TableName("sys_workflow_output")
public class WorkflowOutput implements Serializable {

    /** 产物类型：文本（文本类一般不入本表，保留枚举完整性） */
    public static final int TYPE_TEXT = 1;
    /** 产物类型：图片 */
    public static final int TYPE_IMAGE = 2;
    /** 产物类型：视频 */
    public static final int TYPE_VIDEO = 3;
    /** 产物类型：音频 */
    public static final int TYPE_AUDIO = 4;
    /** 产物类型：普通文件 */
    public static final int TYPE_FILE = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** sys_workflow_run.run_id */
    private String runId;

    /** 节点 ID，如 n1 */
    private String nodeId;

    /** 节点名称快照（工作流改名后仍显示当时名称） */
    private String nodeName;

    /** 产出该产物的工具编码 */
    private String toolCode;

    /** 对应第几个输入（多文件并行时区分） */
    private Integer fileIndex;

    /** 产物类型，见 TYPE_* 常量 */
    private Integer outputType;

    /** 文本内容（outputType=1 时有值） */
    private String textContent;

    /** 文件名（outputType!=1 时有值） */
    private String fileName;

    /** 文件访问地址（COS 地址或签名 URL） */
    private String fileUrl;

    /** COS 对象 key（签名 URL 过期后可据此重新签发） */
    private String cosKey;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME 类型，如 image/png */
    private String mimeType;

    /** 图片/视频宽度（像素） */
    private Integer width;

    /** 图片/视频高度（像素） */
    private Integer height;

    /** 音视频时长（毫秒） */
    private Integer durationMs;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
