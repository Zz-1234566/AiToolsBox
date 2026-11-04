package com.example.aitools.agent;

import com.example.aitools.vo.AgentRunVO;

import java.util.List;
import java.util.function.Consumer;

/**
 * 智能体服务。
 *
 * <p>智能体与工作流的分工：工作流是**用户预先编排**的 DAG，
 * 智能体是**LLM 运行时决策**的循环 —— 用户只给目标，模型自己决定调哪个工具、调几次。
 */
public interface AgentService {

    /**
     * 跑一次智能体。
     *
     * <p>通过 {@code onFrame} 回调推送过程帧（格式见各帧常量），
     * 便于 Controller 直接接到 SSE 上。
     * <p>
     * <b>运行记录会自动落库</b>（{@code sys_agent_run}）：开始时建一条「运行中」，
     * 结束时更新为成功 / 失败 / 已取消（含每一步的明细）。
     *
     * @param userId      发起用户
     * @param goal        用户目标
     * @param attachments 附件（可为空）
     * @param modelCode   用户选定的模型编码（BYOK）；为空走默认凭据
     * @param onFrame     过程帧回调
     * @return 本次运行的 runId
     */
    String run(Long userId, String goal, List<AgentAttachment> attachments,
               String modelCode, Consumer<String> onFrame);

    /**
     * 我最近的智能体运行记录（不含步骤明细，避免列表响应体膨胀）。
     *
     * @param userId 用户 ID
     * @param limit  条数上限
     */
    List<AgentRunVO> listRuns(Long userId, int limit);

    /**
     * 运行详情（含步骤明细）。
     *
     * @param userId 用户 ID（只能看自己的）
     * @param runId  运行 ID
     * @return 详情；找不到返回 null
     */
    AgentRunVO getRun(Long userId, String runId);

    // ==================== 过程帧格式 ====================
    // 约定与项目现有 SSE 标记一致：`--- [内容] ---`，前端按内容前缀分发。

    /** 帧前缀：模型的思考 */
    String FRAME_THOUGHT = "思考";
    /** 帧前缀：开始调用工具 */
    String FRAME_TOOL = "工具";
    /** 帧前缀：工具结果结束（结果正文是普通 chunk，不带标记） */
    String FRAME_TOOL_END = "工具结果结束";
    /** 帧前缀：最终答案开始（之后的普通 chunk 是答案正文） */
    String FRAME_ANSWER = "答案";
    /** 帧前缀：全部完成 */
    String FRAME_DONE = "完成";
}
