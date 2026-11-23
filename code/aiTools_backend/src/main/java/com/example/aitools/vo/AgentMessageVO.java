package com.example.aitools.vo;

import lombok.Data;

/**
 * 智能体会话消息出参。
 */
@Data
public class AgentMessageVO {

    /** 会话内序号 */
    private Integer seq;

    /** user / assistant */
    private String role;

    /** 正文 */
    private String content;

    /** 附加信息 JSON 字符串（前端自行解析：步骤/附件/耗时） */
    private String meta;

    /** 创建时间 */
    private String createTime;
}
