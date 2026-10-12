package com.example.aitools.ai;

/**
 * 已解析的 AI 模型调用凭据（明文 Key 已解密）。
 * <p>
 * 由 {@code ModelCredentialResolver.resolve(...)} 返回，AI 客户端拿到后直接使用，
 * 不再回读任何数据库 / 配置文件。
 *
 * @param keyResolverKey   明文 API Key（已解密或显式 k）
 * @param apiUrl          API 入口
 * @param apiModel        实际模型名（可能与 modelCode 不同）
 * @param provider        服务商标识（minimax / tencent / openai）—— 仅日志用
 * @param source          凭据来源（override / user_default / system_default）—— 仅日志用
 */
public record ResolvedCredential(
        String keyResolverKey,
        String apiUrl,
        String apiModel,
        String provider,
        String source
) {
    /** 调用方临时传入 key（最高优先级） */
    public static final String SOURCE_OVERRIDE = "override";
    /** 用户在 sys_user_ai_model 配的默认 key */
    public static final String SOURCE_USER_DEFAULT = "user_default";
    /** 系统 sys_ai_model.default=1 的 key */
    public static final String SOURCE_SYSTEM_DEFAULT = "system_default";
}