package com.example.aitools.service;

import com.example.aitools.dto.UserAiModelDTO;
import com.example.aitools.entity.SysUserAiModel;

import java.util.List;

/**
 * 用户 AI 模型（BYOK）业务服务。
 * <p>
 * 加密 / 解密 / 列表脱敏等细节封装在实现类，对 Controller 只暴露 DTO。
 */
public interface SysUserAiModelService {

    /**
     * 保存（新增 / 更新）。同一 (userId, capability) 下默认键唯一。
     *
     * @param userId       用户 ID
     * @param dto          表单数据（apiKey 明文会被加密入库，<b>不</b>回传）
     * @param modelName    对应 sys_ai_model.model_name，用于 capability 推断
     */
    UserAiModelDTO save(Long userId, UserAiModelDTO dto);

    /**
     * 用户列表：脱敏（仅返回 apiKeyPrefix）。
     */
    List<UserAiModelDTO> listByUser(Long userId);

    /**
     * 设为默认：先把同 (userId, capability) 其他记录的 is_default=0，再设目标=1。
     */
    void setDefault(Long userId, Long id);

    /**
     * 逻辑删除（dr=1）。
     */
    void remove(Long userId, Long id);

    /**
     * 内部用：从 Entity 转 DTO（清除 apiKey 明文）。
     */
    UserAiModelDTO toDTO(SysUserAiModel entity);
}