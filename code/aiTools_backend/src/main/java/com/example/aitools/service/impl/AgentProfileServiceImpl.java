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

    /** 允许的偏好键：跨会话共享的只有「交互偏好」，具体事实一律不进入画像 */
    private static final java.util.Set<String> ALLOWED_KEYS = java.util.Set.of(
            "language", "output_style", "answer_length", "tone",
            "preferred_tools", "industry", "time_preference");

    /** 注入时的可读标签 */
    private static final java.util.Map<String, String> KEY_LABELS = java.util.Map.of(
            "language", "语言", "output_style", "输出风格", "answer_length", "期望答复长度",
            "tone", "语气", "preferred_tools", "常用工具", "industry", "所属行业",
            "time_preference", "时间习惯");

    /** 画像是「慢变量」：每次抽取都需要一次模型调用，限制单次输入长度 */
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_CHARS = 3000;

    private static final String EXTRACT_SYSTEM = """
            你负责提炼用户的**长期交互偏好**。只输出一个 JSON 对象，不要输出其他文字：
            {"preferences":{"key":"value"},"profile_text":"不超过 120 字的偏好摘要"}

            preferences 只允许以下键（没有就省略，不要自造键）：
              language（语言）、output_style（输出风格）、answer_length（期望答复长度）、
              tone（语气）、preferred_tools（常用工具）、industry（所属行业）、time_preference（时间习惯）

            严禁写入任何**具体事实**：姓名、地点、颜色、数字、日期、项目名、账号、文件内容等。
            对话里出现的一次性事实（如"我最喜欢的颜色是蓝色"）不属于偏好，必须忽略。
            profile_text 只概括交互偏好，同样不得包含具体事实。
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

    /**
     * 注入用的画像文本。
     * <p>
     * <b>只拼白名单内的结构化偏好，不注入 profile_text 摘要</b>：
     * 摘要是自然语言，模型抽取时可能混入会话里的具体事实（如"喜欢蓝色"），
     * 一旦注入就会跨会话泄漏 —— 违反「对话窗口隔离」。
     */
    @Override
    public String getProfileText(Long userId) {
        UserProfile p = find(userId);
        if (p == null) return "";
        java.util.Map<String, Object> prefs = parseMap(p.getPreferences());
        StringBuilder sb = new StringBuilder();
        for (String key : ALLOWED_KEYS) {
            Object v = prefs.get(key);
            if (v == null || String.valueOf(v).isBlank()) continue;
            sb.append("- ").append(KEY_LABELS.getOrDefault(key, key))
              .append("：").append(v).append('\n');
        }
        return sb.toString().trim();
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
        String preferences = filterPreferences(root.has("preferences") ? root.get("preferences") : null);

        UserProfile p = find(userId);
        boolean insert = p == null;
        if (insert) {
            p = new UserProfile();
            p.setUserId(userId);
            p.setDr(0);
        }
        if (!profileText.isBlank()) p.setProfileText(profileText);
        // 偏好合并而不是覆盖：新出现的键覆盖旧值，旧键保留（避免画像随最近对话漂移）
        p.setPreferences(mergePreferences(p.getPreferences(), preferences));
        p.setSourceSessionId(sessionId);
        if (insert) {
            userProfileMapper.insert(p);
        } else {
            userProfileMapper.updateById(p);
        }
        log.info("[agent-profile] 画像已更新 userId={} profileLen={}", userId,
                p.getProfileText() == null ? 0 : p.getProfileText().length());
    }

    /**
     * 只保留白名单键的偏好，其余（如 favorite_color 这类具体事实）一律丢弃。
     * <p>
     * 提示词已经禁止模型输出具体事实，但**模型不保证遵守**，
     * 这里做代码级兜底：白名单是「会话隔离」的最后一道闸。
     */
    private String filterPreferences(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e -> {
                if (ALLOWED_KEYS.contains(e.getKey())) {
                    out.put(e.getKey(), e.getValue().asText(""));
                }
            });
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 合并新旧偏好：旧键保留，同键以新值为准。
     * <p>
     * 为什么合并：画像是慢变量，单次对话只应「增量修正」已有偏好，
     * 全量覆盖会让长期稳定的偏好被最近几句对话冲掉。
     */
    @SuppressWarnings("unchecked")
    private String mergePreferences(String oldJson, String newJson) {
        java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.putAll(parseMap(oldJson));
        merged.putAll(parseMap(newJson));
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            return newJson == null || newJson.isBlank() ? "{}" : newJson;
        }
    }

    private java.util.Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            return objectMapper.readValue(json, java.util.Map.class);
        } catch (Exception e) {
            return java.util.Map.of();
        }
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
