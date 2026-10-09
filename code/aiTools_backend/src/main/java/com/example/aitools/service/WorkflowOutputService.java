package com.example.aitools.service;

import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.vo.WorkflowOutputVO;

import java.util.List;

/**
 * 工作流产物服务。
 * <p>
 * 文本类产物由节点执行直接写入 {@code node_results.outputs}（前端渲染），
 * 本服务只负责**文件类产物**（图片/视频/音频/文件）的落库与查询。
 */
public interface WorkflowOutputService {

    /**
     * 保存一个文件类产物。
     * <p>
     * 内部先把字节写入 COS（前缀固定 {@code wf-output}），拿到 URL 后再落库。
     *
     * @param runId     工作流运行 ID
     * @param nodeId    节点 ID
     * @param nodeName  节点名称快照
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
    WorkflowOutputVO saveFileOutput(String runId, String nodeId, String nodeName, String toolCode,
                                    int fileIndex, int outputType, byte[] content, String fileName,
                                    String mimeType, Integer width, Integer height, Integer durationMs);

    /**
     * 查询某次运行的所有产物，按节点与输入序号排序。
     *
     * @param runId 工作流运行 ID
     * @return 产物列表
     */
    List<WorkflowOutputVO> listByRunId(String runId);

    /**
     * 查询某次运行的指定节点的产物。
     *
     * @param runId  工作流运行 ID
     * @param nodeId 节点 ID
     * @return 产物列表
     */
    List<WorkflowOutputVO> listByRunAndNode(String runId, String nodeId);
}
