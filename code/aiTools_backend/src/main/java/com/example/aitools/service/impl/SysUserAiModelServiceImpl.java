package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.UserAiModelDTO;
import com.example.aitools.entity.AiModel;
import com.example.aitools.entity.SysUserAiModel;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiModelMapper;
import com.example.aitools.mapper.SysUserAiModelMapper;
import com.example.aitools.service.SysUserAiModelService;
import com.example.aitools.utils.AesKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户 AI 模型 BYOK 业务实现。
 * <p>
 * 写入：明文 Key 加密后存 {@code api_key_encrypted}，明文丢弃。
 * <br>读取：返回的 DTO 不含明文，仅前缀。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserAiModelServiceImpl implements SysUserAiModelService {

    private final SysUserAiModelMapper sysUserAiModelMapper;
    private final AiModelMapper aiModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAiModelDTO save(Long userId, UserAiModelDTO dto) {
        // 1) modelCode 必须存在
        AiModel sysModel = aiModelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getModelCode, dto.getModelCode())
                        .eq(AiModel::getDr, Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        if (sysModel == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "modelCode 不存在: " + dto.getModelCode());
        }

        // 2) capability 优先取 DTO，其次从系统表推
        String capability = dto.getCapability() != null && !dto.getCapability().isBlank()
                ? dto.getCapability()
                : sysModel.getCapability();
        if (capability == null || capability.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "capability 不能为空");
        }

        // 3) 加密 apiKey（仅在新增 / 修改时需要；id 已存在且未传新 apiKey = 不更新加密值）
        SysUserAiModel entity;
        boolean isUpdate = dto.getId() != null && dto.getId() > 0;

        if (isUpdate) {
            entity = sysUserAiModelMapper.selectById(dto.getId());
            if (entity == null || !entity.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "记录不存在");
            }
            // nickname / capability / is_default / status 可更新
            entity.setNickname(dto.getNickname());
            entity.setCapability(capability);
            entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : entity.getIsDefault());
            entity.setStatus(dto.getStatus() != null ? dto.getStatus() : entity.getStatus());
            // 仅当传了新 key 时才重加密
            if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
                entity.setApiKeyEncrypted(AesKeyUtils.encrypt(dto.getApiKey()));
                entity.setApiKeyPrefix(AesKeyUtils.maskPrefix(dto.getApiKey()));
            }
            sysUserAiModelMapper.updateById(entity);
        } else {
            if (dto.getApiKey() == null || dto.getApiKey().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "apiKey 不能为空");
            }
            entity = new SysUserAiModel();
            entity.setUserId(userId);
            entity.setModelCode(dto.getModelCode());
            entity.setApiKeyEncrypted(AesKeyUtils.encrypt(dto.getApiKey()));
            entity.setApiKeyPrefix(AesKeyUtils.maskPrefix(dto.getApiKey()));
            entity.setNickname(dto.getNickname());
            entity.setCapability(capability);
            entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);
            entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
            entity.setLastUsedAt(null);
            entity.setDr(0);
            sysUserAiModelMapper.insert(entity);
        }

        // 4) 若设了默认 → 把同 (userId, capability) 其他默认清除
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            LambdaUpdateWrapper<SysUserAiModel> uw = new LambdaUpdateWrapper<>();
            uw.eq(SysUserAiModel::getUserId, userId)
                    .eq(SysUserAiModel::getCapability, capability)
                    .eq(SysUserAiModel::getIsDefault, 1)
                    .ne(SysUserAiModel::getId, entity.getId())
                    .set(SysUserAiModel::getIsDefault, 0);
            sysUserAiModelMapper.update(null, uw);
        }

        return toDTO(entity);
    }

    @Override
    public List<UserAiModelDTO> listByUser(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<SysUserAiModel> list = sysUserAiModelMapper.selectList(
                new LambdaQueryWrapper<SysUserAiModel>()
                        .eq(SysUserAiModel::getUserId, userId)
                        .eq(SysUserAiModel::getDr, Constants.DR_NORMAL)
                        .orderByDesc(SysUserAiModel::getIsDefault)
                        .orderByDesc(SysUserAiModel::getCreateTime));
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        SysUserAiModel target = sysUserAiModelMapper.selectById(id);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "记录不存在");
        }
        // 先清同 capability 默认
        LambdaUpdateWrapper<SysUserAiModel> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysUserAiModel::getUserId, userId)
                .eq(SysUserAiModel::getCapability, target.getCapability())
                .eq(SysUserAiModel::getIsDefault, 1)
                .set(SysUserAiModel::getIsDefault, 0);
        sysUserAiModelMapper.update(null, uw);
        // 再设目标
        target.setIsDefault(1);
        sysUserAiModelMapper.updateById(target);
    }

    @Override
    public void remove(Long userId, Long id) {
        SysUserAiModel target = sysUserAiModelMapper.selectById(id);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "记录不存在");
        }
        // 逻辑删除（@TableLogic 自动把 dr=1）
        sysUserAiModelMapper.deleteById(id);
    }

    @Override
    public UserAiModelDTO toDTO(SysUserAiModel entity) {
        if (entity == null) return null;
        UserAiModelDTO dto = new UserAiModelDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setModelCode(entity.getModelCode());
        // ⚠️ 永远不返回明文 / 加密值，只返前缀
        dto.setApiKeyPrefix(entity.getApiKeyPrefix());
        dto.setNickname(entity.getNickname());
        dto.setCapability(entity.getCapability());
        dto.setIsDefault(entity.getIsDefault());
        dto.setStatus(entity.getStatus());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}