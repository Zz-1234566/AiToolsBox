package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.entity.AiPrompt;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiPromptMapper;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.vo.SystemPromptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    private final AiPromptMapper aiPromptMapper;
    private final AiToolMapper aiToolMapper;

    @Override
    public List<SystemPromptVO> listByTool(String toolCode) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPrompt::getToolCode, toolCode)
                .eq(AiPrompt::getDr, Constants.DR_NORMAL)
                .orderByAsc(AiPrompt::getId);
        return aiPromptMapper.selectList(wrapper).stream()
                .map(p -> {
                    SystemPromptVO vo = new SystemPromptVO();
                    vo.setId(p.getId());
                    vo.setPromptName(p.getPromptName());
                    vo.setPromptType(p.getPromptType());
                    vo.setPromptUse(p.getPromptUse());
                    vo.setPromptText(p.getPromptContent());
                    return vo;
                })
                .toList();
    }

    @Override
    public AiPrompt getById(Long promptId) {
        AiPrompt prompt = aiPromptMapper.selectById(promptId);
        if (prompt == null) {
            log.error("提示词不存在: promptId={}", promptId);
            throw new BusinessException("提示词不存在");
        }
        return prompt;
    }

    @Override
    public AiPrompt getDefault(String toolCode, String promptType) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPrompt::getToolCode, toolCode)
                .eq(AiPrompt::getPromptType, promptType)
                .eq(AiPrompt::getDr, Constants.DR_NORMAL)
                .orderByAsc(AiPrompt::getId)
                .last("LIMIT 1");
        return aiPromptMapper.selectOne(wrapper);
    }

    @Override
    public AiPrompt getDefaultByUse(String toolCode, String promptUse) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPrompt::getToolCode, toolCode)
                .eq(AiPrompt::getPromptUse, promptUse)
                .eq(AiPrompt::getDr, Constants.DR_NORMAL)
                .orderByAsc(AiPrompt::getId)
                .last("LIMIT 1");
        return aiPromptMapper.selectOne(wrapper);
    }

    @Override
    public String resolvePrompt(String userProvided, Long promptId, String promptUse, String toolCode) {
        if (userProvided != null && !userProvided.isBlank()) {
            return userProvided.trim();
        }
        if (promptId != null) {
            AiPrompt selected = getById(promptId);
            if (promptUse.equals(selected.getPromptUse())) {
                return selected.getPromptContent();
            }
            // 用途不匹配，回退到该工具默认
        }
        AiPrompt defaultPrompt = getDefaultByUse(toolCode, promptUse);
        return defaultPrompt == null ? null : defaultPrompt.getPromptContent();
    }

    @Override
    public Long findToolIdByCode(String toolCode) {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getToolCode, toolCode)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .last("LIMIT 1");
        AiTool tool = aiToolMapper.selectOne(wrapper);
        return tool == null ? null : tool.getId();
    }
}
