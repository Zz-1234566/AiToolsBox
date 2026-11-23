package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.ai.AiClient;
import com.example.aitools.ai.ResolvedCredential;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.entity.UserProfile;
import com.example.aitools.mapper.UserProfileMapper;
import com.example.aitools.service.AgentProfileService;
import com.example.aitools.vo.AgentMessageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 用户画像服务实现。
 * <p>
 * 抽取放在智能体线程池里异步执行：画像只影响「下一次」对话的提示词，
 * 不值得让用户等这一次回答。
 */
@Slf4j
@Service
public class AgentProfileServiceImpl implements AgentProfileService {

    /** 画像是「慢变量」：每次抽取都需要一次模型调用，限制单次输入长度 */
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_CHARS = 3000;

    private static final String EXTRACT_SYSTEM = """
            你负责从对话中提炼用户画像。只输出一个 JSON 对象，不要输出其他文字：
            {"preferences":{"key":"value"},"profile_text":"不超过 120 字的画像摘要"}
            preferences 只放稳定偏好（如 语言、输出风格、常用工具、行业、时间安排）；
            profile_text 用第三人称概括用户身份、关注点与偏好，不要编造未出现的信息。
            """;

    private final UserProfileMapper userProfileMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService agentExecutor;

    public AgentProfileServiceImpl(UserProfileMapper userProfileMapper,
                                   AiClient aiClient,
                                   ObjectMapper objectMapper,
                                   @Qualifier(ExecutorConfig.AGENT_EXECUTOR) ExecutorService agentExecutor) {
        this.userProfileMapper = userProfileMapper;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.agentExecutor = agentExecutor;
    }

    @Override
    public String getProfileText(Long userId) {
        UserProfile p = find(userId);
        return p == null || p.getProfileText() == null ? "" : p.getProfileText();
    }

    @Override
    public void updateAsync(Long userId, String sessionId, List<AgentMessageVO> messages, ResolvedCredential cred) {
        if (userId == null || messages == null || messages.isEmpty()) return;
        try {
            agentExecutor.submit(() -> {
                try {
                    doUpdate(userId, sessionId, messages, cred);
                } catch (Exception e) {
                    log.warn("[agent-profile] 画像更新失败 userId={}: {}", userId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[agent-profile] 提交异步任务失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private void doUpdate(Long userId, String sessionId, List<AgentMessageVO> messages, ResolvedCredential cred) throws Exception {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, messages.size() - MAX_MESSAGES);
        for (int i = from; i < messages.size(); i++) {
            AgentMessageVO m = messages.get(i);
            String role = "user".equals(m.getRole()) ? "用户" : "助手";
            sb.append(role).append("：").append(m.getContent() == null ? "" : m.getContent()).append('\n');
            if (sb.length() > MAX_CHARS) break;
        }
        String raw = aiClient.chat(EXTRACT_SYSTEM, sb.toString(), cred);
        JsonNode root = objectMapper.readTree(extractJson(raw));
        String profileText = root.path("profile_text").asText("");
        String preferences = root.has("preferences") ? root.get("preferences").toString() : "{}";

        UserProfile p = find(userId);
        boolean insert = p == null;
        if (insert) {
            p = new UserProfile();
            p.setUserId(userId);
            p.setDr(0);
        }
        if (!profileText.isBlank()) p.setProfileText(profileText);
        p.setPreferences(preferences);
        p.setSourceSessionId(sessionId);
        if (insert) {
            userProfileMapper.insert(p);
        } else {
            userProfileMapper.updateById(p);
        }
        log.info("[agent-profile] 画像已更新 userId={} profileLen={}", userId,
                p.getProfileText() == null ? 0 : p.getProfileText().length());
    }

    /** 模型可能把 JSON 包在 ```json 里，这里做一次兜底提取 */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int l = raw.indexOf('{');
        int r = raw.lastIndexOf('}');
        return (l >= 0 && r > l) ? raw.substring(l, r + 1) : "{}";
    }

    private UserProfile find(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("LIMIT 1"));
    }
}
