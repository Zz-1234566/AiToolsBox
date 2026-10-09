package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具产物（sys_tool_output）——全局工具产物表。
 * <p>
 * 本表是全局产物表：任何工具产出文件（图片/视频/音频/文件）都写这一张表，
 * 不仅限于工作流节点。文件实体存 COS，表里只存 URL 与元信息。
 * <p>
 * 与 {@code sys_workflow_run.node_results.outputs} 的关系（仅工作流来源）：
 * <ul>
 *   <li>文本类产物仍留在 {@code node_results}（前端直接渲染）；</li>
 *   <li>文件类产物（图片/视频/音频/文件）存本表。</li>
 * </ul>
 * {@code runId} / {@code nodeId} / {@code nodeName} 只有工作流来源才有值；
 * 独立调用工具时为 null（无工作流上下文）。
 * <table border="1">
 *   <caption>来源与字段取值</caption>
 *   <tr><th>来源</th><th>runId</th><th>nodeId</th><th>nodeName</th><th>toolCode</th></tr>
 *   <tr><td>工作流节点产出</td><td>有值</td><td>有值</td><td>有值</td><td>有值</td></tr>
 *   <tr><td>独立调用工具产出</td><td>null</td><td>null</td><td>null</td><td>有值</td></tr>
 * </table>
 * <p>
 * 定位维度：{@code toolCode + runId + nodeId + fileIndex} 联合定位，
 * 其中 {@code runId} / {@code nodeId} 可为 null（非工作流来源）；
 * 工作流来源下与 {@code node_results[nid].outputs} 的下标一一对应（N 个输入 → N 个输出）。
 */
@Data
@TableName("sys_tool_output")
public class ToolOutput implements Serializable {

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

    /** sys_workflow_run.run_id；仅工作流来源有值，独立调用工具时为 null */
    private String runId;

    /** 节点 ID，如 n1；仅工作流来源有值，独立调用工具时为 null */
    private String nodeId;

    /** 节点名称快照（工作流改名后仍显示当时名称）；仅工作流来源有值，独立调用工具时为 null */
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
