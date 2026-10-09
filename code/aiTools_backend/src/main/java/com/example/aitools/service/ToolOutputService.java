package com.example.aitools.service;

import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.vo.ToolOutputVO;

import java.util.List;

/**
 * 工具产物服务（对应全局工具产物表 {@code sys_tool_output}）。
 * <p>
 * 本服务是全局的：任何工具产出文件（图片/视频/音频/文件）都通过本服务落库，
 * 不仅限于工作流节点。工作流来源的文本类产物仍由节点执行直接写入
 * {@code node_results.outputs}（前端渲染），本服务只负责**文件类产物**的落库与查询。
 * <p>
 * {@code runId} / {@code nodeId} / {@code nodeName} 只有工作流来源才有值；
 * 独立调用工具时为 null（无工作流上下文），表中对应列允许 NULL。
 */
public interface ToolOutputService {

    /**
     * 保存一个文件类产物。
     * <p>
     * 内部先把字节写入 COS（前缀固定 {@code tool-output}），拿到 URL 后再落库。
     * <p>
     * {@code runId} / {@code nodeId} / {@code nodeName} 传 null 表示**非工作流来源**
     * （工具被独立调用，如用户单独点「证件照换背景色」按钮）：产物照样写本表，
     * 只是没有工作流上下文，此时仅靠 {@code toolCode} + {@code fileIndex} 关联；
     * 工作流来源请传入真实的 runId/nodeId，便于按运行记录回溯产物
     * （与 node_results[nid].outputs 的下标一一对应）。
     *
     * @param runId     工作流运行 ID；非工作流来源传 null
     * @param nodeId    节点 ID；非工作流来源传 null
     * @param nodeName  节点名称快照；非工作流来源传 null
     * @param toolCode  产出该产物的工具编码
     * @param fileIndex 对应第几个输入
     * @param outputType 产物类型（2图片 3视频 4音频 5文件）
     * @param content   文件字节
     * @param fileName  文件名（含扩展名）
     * @param mimeType  MIME 类型；为空时按扩展名推断
     * @param width     宽（可选）
     * @param height    高（可选）
     * @param durationMs 时长毫秒（可选）
     * @return 落库后的产物
     */
    ToolOutputVO saveFileOutput(String runId, String nodeId, String nodeName, String toolCode,
                                    int fileIndex, int outputType, byte[] content, String fileName,
                                    String mimeType, Integer width, Integer height, Integer durationMs);

    /**
     * 查询某次运行的所有产物，按节点与输入序号排序。
     *
     * @param runId 工作流运行 ID
     * @return 产物列表
     */
    List<ToolOutputVO> listByRunId(String runId);

    /**
     * 查询某次运行的指定节点的产物。
     *
     * @param runId  工作流运行 ID
     * @param nodeId 节点 ID
     * @return 产物列表
     */
    List<ToolOutputVO> listByRunAndNode(String runId, String nodeId);
}
