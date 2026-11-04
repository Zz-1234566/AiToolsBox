package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体运行记录（sys_agent_run）。
 *
 * <p>一次运行 = 用户给的一个目标 + AI 自己走出来的每一步。
 */
@Data
@TableName("sys_agent_run")
public class AgentRun implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外 runId（UUID） */
    private String runId;

    /** 所属用户 ID */
    private Long userId;

    /** 用户目标 */
    private String goal;

    /** 状态：0待运行 1运行中 2成功 3失败 4已取消 */
    private Integer status;

    /** 步骤 JSON 数组（见 AgentStep） */
    private String steps;

    /** 最终答案 */
    private String finalOutput;

    /** 实际步数（工具调用次数） */
    private Integer stepCount;

    /** 总耗时（毫秒） */
    private Integer duration;

    /** 整体错误信息 */
    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime finishedAt;

    @TableLogic
    private Integer dr;
}
