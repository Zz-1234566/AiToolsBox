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

    /**
     * 用户显式指定 modelCode 时调用：在用户的 sys_user_ai_model 里按 modelCode 查（忽略 isDefault），
     * 找不到则抛 BusinessException。仍然尊重 override（apiKeyOverride 非空直接用）。
     *
     * @param userId         业务用户 ID
     * @param modelCode      用户从前端下拉选中的 modelCode
     * @param apiKeyOverride 临时 Key（可选）
     */
    ResolvedCredential resolveByModelCode(Long userId, String modelCode, String apiKeyOverride);

    /**
     * 前置校验：用户传入的 modelCode 是否存在、capability 是否匹配 requiredCapability。
     * <p>不匹配 → 抛 BusinessException(PARAM_ERROR, "模型 X 不支持 Y，请选择 Z 模型")。
     *
     * @param modelCode          用户从前端下拉选择的 modelCode；null/空 = 不指定（跳过校验，走 Resolver 默认）
     * @param requiredCapability 工具需要的 capability（text / vision / asr / ocr）
     */
    void requireCapability(String modelCode, String requiredCapability);
}