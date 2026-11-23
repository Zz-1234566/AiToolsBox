package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.ResultCode;
import com.example.aitools.common.TokenEstimator;
import com.example.aitools.entity.AgentMessage;
import com.example.aitools.entity.AgentSession;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AgentMessageMapper;
import com.example.aitools.mapper.AgentSessionMapper;
import com.example.aitools.service.AgentSessionService;
import com.example.aitools.vo.AgentMessageVO;
import com.example.aitools.vo.AgentSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能体会话服务实现。
 * <p>
 * 所有查询都带 userId 条件，避免越权读取他人的会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionServiceImpl implements AgentSessionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;

    @Override
    public AgentSessionVO create(Long userId) {
        AgentSession s = new AgentSession();
        s.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        s.setUserId(userId);
        s.setTitle(DEFAULT_TITLE);
        s.setSummarizedSeq(0);
        s.setMessageCount(0);
        s.setLastActiveTime(LocalDateTime.now());
        s.setDr(0);
        sessionMapper.insert(s);
        log.info("[agent-session] 新建会话 userId={} sessionId={}", userId, s.getSessionId());
        return toVO(s);
    }

    @Override
    public List<AgentSessionVO> list(Long userId, int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        List<AgentSession> list = sessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getLastActiveTime)
                .last("LIMIT " + size));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public AgentSessionVO rename(Long userId, String sessionId, String title) {
        AgentSession s = requireSession(userId, sessionId);
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "标题不能为空");
        }
        if (t.length() > 64) {
            t = t.substring(0, 64);
        }
        s.setTitle(t);
        sessionMapper.updateById(s);
        return toVO(s);
    }

    @Override
    public void delete(Long userId, String sessionId) {
        AgentSession s = requireSession(userId, sessionId);
        sessionMapper.deleteById(s.getId());
        // 消息一并逻辑删除（按会话ID）
        messageMapper.delete(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, s.getSessionId())
                .eq(AgentMessage::getUserId, userId));
        log.info("[agent-session] 删除会话 userId={} sessionId={}", userId, sessionId);
    }

    @Override
    public List<AgentMessageVO> messages(Long userId, String sessionId, int limit) {
        AgentSession s = requireSession(userId, sessionId);
        int size = limit <= 0 ? 200 : Math.min(limit, 500);
        List<AgentMessage> list = messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, s.getSessionId())
                .eq(AgentMessage::getUserId, userId)
                .orderByAsc(AgentMessage::getSeq)
                .last("LIMIT " + size));
        List<AgentMessageVO> out = new ArrayList<>(list.size());
        for (AgentMessage m : list) {
            AgentMessageVO vo = new AgentMessageVO();
            vo.setSeq(m.getSeq());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setMeta(m.getMeta());
            vo.setCreateTime(m.getCreateTime() == null ? null : m.getCreateTime().format(FMT));
            out.add(vo);
        }
        return out;
    }

    @Override
    public AgentSession requireSession(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "缺少会话标识");
        }
        AgentSession s = sessionMapper.selectOne(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getSessionId, sessionId)
                .eq(AgentSession::getUserId, userId)
                .last("LIMIT 1"));
        if (s == null) {
            // 前端带了未知 sessionId：视为新会话（避免运行直接失败）
            AgentSession created = new AgentSession();
            created.setSessionId(sessionId);
            created.setUserId(userId);
            created.setTitle(DEFAULT_TITLE);
            created.setSummarizedSeq(0);
            created.setMessageCount(0);
            created.setLastActiveTime(LocalDateTime.now());
            created.setDr(0);
            sessionMapper.insert(created);
            log.info("[agent-session] 会话不存在，自动创建 userId={} sessionId={}", userId, sessionId);
            return created;
        }
        return s;
    }

    @Override
    public void appendMessage(AgentSession session, String role, String content, String meta) {
        int nextSeq = (session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1;
        AgentMessage m = new AgentMessage();
        m.setSessionId(session.getSessionId());
        m.setUserId(session.getUserId());
        m.setSeq(nextSeq);
        m.setRole(role);
        m.setContent(content);
        m.setMeta(meta);
        m.setTokenEst(TokenEstimator.estimate(content));
        m.setDr(0);
        messageMapper.insert(m);

        session.setMessageCount(nextSeq);
        session.setLastActiveTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Override
    public void autoTitle(AgentSession session, String text) {
        if (session.getTitle() != null && !DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return;
        }
        if (t.length() > TITLE_MAX_LENGTH) {
            t = t.substring(0, TITLE_MAX_LENGTH) + "…";
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<AgentSession>()
                .eq(AgentSession::getId, session.getId())
                .set(AgentSession::getTitle, t));
        session.setTitle(t);
    }

    private AgentSessionVO toVO(AgentSession s) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.setSessionId(s.getSessionId());
        vo.setTitle(s.getTitle());
        vo.setMessageCount(s.getMessageCount());
        vo.setLastActiveTime(s.getLastActiveTime() == null ? null : s.getLastActiveTime().format(FMT));
        vo.setCreateTime(s.getCreateTime() == null ? null : s.getCreateTime().format(FMT));
        return vo;
    }
}
