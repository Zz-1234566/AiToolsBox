package com.example.aitools.service;

import com.example.aitools.ai.ResolvedCredential;
import com.example.aitools.vo.AgentMessageVO;

import java.util.List;

/**
 * 用户画像服务：从对话中抽取偏好与画像摘要，供后续 system prompt 注入。
 */
public interface AgentProfileService {

    /**
     * 取用户画像摘要（注入提示词用）。
     *
     * @return 画像摘要；没有画像时返回空串
     */
    String getProfileText(Long userId);

    /**
     * 异步从最近对话中抽取画像并落库（不阻塞回答）。
     *
     * @param userId     用户
     * @param sessionId  来源会话
     * @param messages   最近的对话消息
     * @param cred       模型凭据（可为 null，走默认）
     */
    void updateAsync(Long userId, String sessionId, List<AgentMessageVO> messages, ResolvedCredential cred);
}
