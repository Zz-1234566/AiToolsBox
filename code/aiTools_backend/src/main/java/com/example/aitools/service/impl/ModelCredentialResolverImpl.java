package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.ai.ResolvedCredential;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.entity.AiModel;
import com.example.aitools.entity.SysUserAiModel;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiModelMapper;
import com.example.aitools.mapper.SysUserAiModelMapper;
import com.example.aitools.service.ModelCredentialResolver;
import com.example.aitools.utils.AesKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 凭据解析器实现：
 * <ol>
 *   <li>override 非空 → source=override</li>
 *   <li>userId 非空 → sys_user_ai_model 中 is_default=1 的密文 Key 解密 → source=user_default</li>
 *   <li>sys_ai_model.is_default=1 明文 api_key → source=system_default</li>
 *   <li>都拿不到 → 抛 BusinessException</li>
 * </ol>
 *
 * <b>解密失败</b>统一抛 BusinessException，前端只看到 "未配置可用 AI 模型"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCredentialResolverImpl implements ModelCredentialResolver {

    private final SysUserAiModelMapper sysUserAiModelMapper;
    private final AiModelMapper aiModelMapper;

    @Override
    public ResolvedCredential resolve(Long userId, String capability, String apiKeyOverride) {
        // -------- 1. override 优先 --------
        if (apiKeyOverride != null && !apiKeyOverride.isBlank()) {
            // override 模式下，url/model/provider 仍取系统默认（兼容常见用法）
            AiModel sysDefault = findSystemDefault(capability);
            return new ResolvedCredential(
                    apiKeyOverride,
                    sysDefault == null ? null : sysDefault.getApiUrl(),
                    sysDefault == null ? null : sysDefault.getApiModel(),
                    sysDefault == null ? null : sysDefault.getProvider(),
                    ResolvedCredential.SOURCE_OVERRIDE);
        }

        // -------- 2. 用户默认 --------
        if (userId != null) {
            LambdaQueryWrapper<SysUserAiModel> w = new LambdaQueryWrapper<>();
            w.eq(SysUserAiModel::getUserId, userId)
                    .eq(SysUserAiModel::getCapability, capability)
                    .eq(SysUserAiModel::getIsDefault, 1)
                    .eq(SysUserAiModel::getStatus, 1)
                    .eq(SysUserAiModel::getDr, Constants.DR_NORMAL)
                    .last("LIMIT 1");
            SysUserAiModel userModel = sysUserAiModelMapper.selectOne(w);
            if (userModel != null) {
                String plain = null;
                try {
                    plain = AesKeyUtils.decrypt(userModel.getApiKeyEncrypted());
                } catch (Exception e) {
                    log.error("[Resolver] 用户 key 解密失败 userId={} modelCode={}", userId, userModel.getModelCode());
                }
                if (plain != null && !plain.isBlank()) {
                    return new ResolvedCredential(
                            plain,
                            null,
                            null,
                            "user",
                            ResolvedCredential.SOURCE_USER_DEFAULT);
                }
            }
        }

        // -------- 3. 系统默认 --------
        AiModel sysModel = findSystemDefault(capability);
        if (sysModel != null && sysModel.getApiKey() != null && !sysModel.getApiKey().isBlank()) {
            return new ResolvedCredential(
                    sysModel.getApiKey(),
                    sysModel.getApiUrl(),
                    sysModel.getApiModel(),
                    sysModel.getProvider(),
                    ResolvedCredential.SOURCE_SYSTEM_DEFAULT);
        }

        // -------- 4. 兜底失败 --------
        log.warn("[Resolver] 未找到可用凭据 userId={} capability={}", userId, capability);
        throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                "未配置可用 AI 模型，请联系管理员");
    }

    /**
     * 查系统默认模型（sys_ai_model.is_default=1, status=1, dr=0, capability=?）。
     */
    private AiModel findSystemDefault(String capability) {
        LambdaQueryWrapper<AiModel> w = new LambdaQueryWrapper<>();
        w.eq(AiModel::getCapability, capability)
                .eq(AiModel::getIsDefault, 1)
                .eq(AiModel::getStatus, 1)
                .eq(AiModel::getDr, Constants.DR_NORMAL)
                .last("LIMIT 1");
        return aiModelMapper.selectOne(w);
    }
}