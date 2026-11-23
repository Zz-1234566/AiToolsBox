package com.example.aitools.service;

import com.example.aitools.entity.AgentSession;
import com.example.aitools.vo.AgentMessageVO;
import com.example.aitools.vo.AgentSessionVO;

import java.util.List;

/**
 * 智能体会话服务：会话的增删改查 + 消息追加。
 * <p>
 * 运行流程通过 {@link #requireSession} / {@link #appendMessage} 接入，
 * 保证「一次对话 = 一个会话」的语义。
 */
public interface AgentSessionService {

    /** 默认会话标题 */
    String DEFAULT_TITLE = "新对话";

    /** 自动标题最大长度 */
    int TITLE_MAX_LENGTH = 20;

    /** 新建会话 */
    AgentSessionVO create(Long userId);

    /** 最近会话列表（按最后活跃时间倒序） */
    List<AgentSessionVO> list(Long userId, int limit);

    /** 重命名 */
    AgentSessionVO rename(Long userId, String sessionId, String title);

    /** 删除（逻辑删除，连带消息） */
    void delete(Long userId, String sessionId);

    /** 会话消息（按 seq 升序） */
    List<AgentMessageVO> messages(Long userId, String sessionId, int limit);

    /**
     * 取出会话；不存在则按传入的 sessionId 新建一个（用于「前端先带一个临时 id 过来」的场景）。
     *
     * @throws com.example.aitools.exception.BusinessException 会话不属于当前用户时
     */
    AgentSession requireSession(Long userId, String sessionId);

    /** 追加一条消息：维护 seq / messageCount / lastActiveTime */
    void appendMessage(AgentSession session, String role, String content, String meta);

    /** 标题仍是默认值时，用首条用户消息截取生成标题 */
    void autoTitle(AgentSession session, String text);

    /** 写入压缩摘要与已压缩位置 */
    void updateSummary(AgentSession session, String summary, int summarizedSeq);
}
