package com.example.aitools.service;

import com.example.aitools.ai.ResolvedCredential;

/**
 * AI 模型凭据解析器：把 {@code (userId, capability, override)} 解析成一条完整的调用参数。
 *
 * @see com.example.aitools.ai.ResolvedCredential
 */
public interface ModelCredentialResolver {

    /**
     * 三级优先级：override &gt; user_default &gt; system_default。
     *
     * @param userId          业务用户 ID；为 null 时跳过 user_default 级
     * @param capability      能力标识（text / vision / asr / ocr）
     * @param apiKeyOverride  调用方显式传入的 Key（可选）；非空时直接使用
     * @return 已解密 / 已选定的凭据
     */
    ResolvedCredential resolve(Long userId, String capability, String apiKeyOverride);
}