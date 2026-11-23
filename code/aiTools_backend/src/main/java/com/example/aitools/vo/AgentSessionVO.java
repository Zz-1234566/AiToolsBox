package com.example.aitools.vo;

import lombok.Data;

/**
 * 智能体会话出参（不含内部压缩摘要等实现细节）。
 */
@Data
public class AgentSessionVO {

    /** 会话ID（UUID） */
    private String sessionId;

    /** 标题 */
    private String title;

    /** 消息条数 */
    private Integer messageCount;

    /** 最后活跃时间 */
    private String lastActiveTime;

    /** 创建时间 */
    private String createTime;
}
