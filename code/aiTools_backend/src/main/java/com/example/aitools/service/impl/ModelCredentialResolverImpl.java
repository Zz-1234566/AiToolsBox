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
                    // 按 modelCode 查 sys_ai_model 取 url / model / provider（BYOK 跨厂商场景必须）
                    AiModel modelCfg = aiModelMapper.selectOne(
                            new LambdaQueryWrapper<AiModel>()
                                    .eq(AiModel::getModelCode, userModel.getModelCode())
                                    .eq(AiModel::getDr, Constants.DR_NORMAL)
                                    .last("LIMIT 1"));
                    return new ResolvedCredential(
                            plain,
                            modelCfg == null ? null : modelCfg.getApiUrl(),
                            modelCfg == null ? null : modelCfg.getApiModel(),
                            modelCfg == null ? "user" : modelCfg.getProvider(),
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

    @Override
    public ResolvedCredential resolveByModelCode(Long userId, String modelCode, String apiKeyOverride) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "modelCode 不能为空");
        }
        // override 仍优先（用户临时传 key 时直接用，不再走 DB）
        if (apiKeyOverride != null && !apiKeyOverride.isBlank()) {
            AiModel m = aiModelMapper.selectOne(
                    new LambdaQueryWrapper<AiModel>()
                            .eq(AiModel::getModelCode, modelCode)
                            .eq(AiModel::getDr, Constants.DR_NORMAL)
                            .last("LIMIT 1"));
            return new ResolvedCredential(
                    apiKeyOverride,
                    m == null ? null : m.getApiUrl(),
                    m == null ? null : m.getApiModel(),
                    m == null ? "user" : m.getProvider(),
                    ResolvedCredential.SOURCE_OVERRIDE);
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "未登录");
        }
        SysUserAiModel rec = sysUserAiModelMapper.selectOne(
                new LambdaQueryWrapper<SysUserAiModel>()
                        .eq(SysUserAiModel::getUserId, userId)
                        .eq(SysUserAiModel::getModelCode, modelCode)
                        .eq(SysUserAiModel::getStatus, 1)
                        .eq(SysUserAiModel::getDr, Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        if (rec == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "未找到该模型的用户配置: " + modelCode);
        }
        String plain;
        try {
            plain = AesKeyUtils.decrypt(rec.getApiKeyEncrypted());
        } catch (Exception e) {
            log.error("[Resolver] 用户 key 解密失败 userId={} modelCode={}", userId, modelCode);
            plain = null;
        }
        if (plain == null || plain.isBlank()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "用户 Key 解密失败");
        }
        AiModel cfg = aiModelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getModelCode, modelCode)
                        .eq(AiModel::getDr, Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        return new ResolvedCredential(
                plain,
                cfg == null ? null : cfg.getApiUrl(),
                cfg == null ? null : cfg.getApiModel(),
                cfg == null ? "user" : cfg.getProvider(),
                ResolvedCredential.SOURCE_USER_DEFAULT);
    }

    @Override
    public void requireCapability(String modelCode, String requiredCapability) {
        if (modelCode == null || modelCode.isBlank()) return;   // 未指定 → 由 resolve() 兜底
        if (requiredCapability == null || requiredCapability.isBlank()) return;
        AiModel model = aiModelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getModelCode, modelCode)
                        .eq(AiModel::getDr, Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        if (model == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "模型不存在: " + modelCode);
        }
        String cap = model.getCapability() == null ? "" : model.getCapability();
        if (!cap.equals(requiredCapability)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "模型 " + modelCode + " 不支持「" + requiredCapability + "」（实际能力=" + cap + "）");
        }
    }
}