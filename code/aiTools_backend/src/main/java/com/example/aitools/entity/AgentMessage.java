package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体会话消息（sys_agent_message）。
 * <p>
 * seq 在会话内从 1 递增；meta 存步骤/附件/耗时等附加信息的 JSON。
 */
@Data
@TableName("sys_agent_message")
public class AgentMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话ID */
    private String sessionId;

    /** 所属用户 */
    private Long userId;

    /** 会话内序号（从 1 递增） */
    private Integer seq;

    /** user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** 附加信息 JSON（步骤/附件/耗时/runId） */
    private String meta;

    /** 估算 token 数 */
    private Integer tokenEst;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
