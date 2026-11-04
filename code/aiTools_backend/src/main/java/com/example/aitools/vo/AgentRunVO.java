package com.example.aitools.vo;

import com.example.aitools.agent.dto.AgentStep;
import lombok.Data;

import java.util.List;

/**
 * 智能体运行记录出参。
 */
@Data
public class AgentRunVO {

    /** 对外 runId */
    private String runId;

    /** 用户目标 */
    private String goal;

    /** 状态：0待运行 1运行中 2成功 3失败 4已取消 */
    private Integer status;

    /** 状态中文（来自 AgentRunStatusEnum，前端直接用，不在前端硬编码映射） */
    private String statusLabel;

    /** 步骤明细（仅详情返回；列表接口不返回，避免响应体膨胀） */
    private List<AgentStep> steps;

    /** 最终答案 */
    private String finalOutput;

    /** 实际步数（工具调用次数） */
    private Integer stepCount;

    /** 总耗时（毫秒） */
    private Integer duration;

    /** 整体错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private String createTime;

    /** 完成时间 */
    private String finishedAt;
}
