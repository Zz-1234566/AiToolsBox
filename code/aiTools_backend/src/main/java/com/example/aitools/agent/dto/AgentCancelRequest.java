package com.example.aitools.agent.dto;

import lombok.Data;

/**
 * 取消智能体运行的请求。
 */
@Data
public class AgentCancelRequest {

    /** 要取消的运行 ID（首帧 {@code --- [运行:xxx] ---} 下发） */
    private String runId;
}
