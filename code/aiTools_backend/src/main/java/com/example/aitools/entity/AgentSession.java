package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体会话（sys_agent_session）。
 * <p>
 * 一个会话 = 一个连续的对话窗口；消息见 {@link AgentMessage}。
 * summary 是上下文压缩的产物：summarizedSeq 之前的消息已被折叠进摘要。
 */
@Data
@TableName("sys_agent_session")
public class AgentSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外会话ID（UUID） */
    private String sessionId;

    /** 所属用户 */
    private Long userId;

    /** 会话标题（首条用户消息截取，可重命名） */
    private String title;

    /** 历史上下文摘要（压缩产物） */
    private String summary;

    /** 已压缩到的消息序号（含） */
    private Integer summarizedSeq;

    /** 消息条数 */
    private Integer messageCount;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}
