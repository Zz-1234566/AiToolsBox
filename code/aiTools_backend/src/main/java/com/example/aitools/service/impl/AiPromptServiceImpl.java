package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.PromptRequest;
import com.example.aitools.entity.AiTool;
import com.example.aitools.entity.AiUserPrompt;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.mapper.AiUserPromptMapper;
import com.example.aitools.service.AiPromptService;
import com.example.aitools.vo.PromptVO;
import com.example.aitools.vo.ToolOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl implements AiPromptService {

    private final AiUserPromptMapper aiUserPromptMapper;
    private final AiToolMapper aiToolMapper;

    @Override
    public List<PromptVO> list(Long userId, String toolCode) {
        LambdaQueryWrapper<AiUserPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getToolCode, toolCode)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .orderByDesc(AiUserPrompt::getCreateTime);
        return aiUserPromptMapper.selectList(wrapper).stream().map(p -> {
            PromptVO vo = new PromptVO();
            vo.setId(p.getId());
            vo.setPromptText(p.getPromptText());
            vo.setPromptUse(p.getPromptUse());
            vo.setToolCode(p.getToolCode());
            vo.setCreateTime(p.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public Long add(Long userId, PromptRequest request) {
        AiUserPrompt prompt = new AiUserPrompt();
        BeanUtils.copyProperties(request, prompt);
        prompt.setUserId(userId);
        prompt.setDr(Constants.DR_NORMAL);
        aiUserPromptMapper.insert(prompt);
        return prompt.getId();
    }

    @Override
    public void update(Long id, Long userId, PromptRequest request) {
        LambdaUpdateWrapper<AiUserPrompt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiUserPrompt::getId, id)
                .eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .set(AiUserPrompt::getPromptText, request.getPromptText())
                .set(AiUserPrompt::getPromptUse, request.getPromptUse())
                .set(AiUserPrompt::getToolCode, request.getToolCode())
                .set(AiUserPrompt::getUpdateTime, LocalDateTime.now());
        if (aiUserPromptMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "提示词不存在或无权操作");
        }
    }

    @Override
    public void delete(Long id, Long userId) {
        LambdaUpdateWrapper<AiUserPrompt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiUserPrompt::getId, id)
                .eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .set(AiUserPrompt::getDr, Constants.DR_DELETED);
        if (aiUserPromptMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "提示词不存在或无权操作");
        }
    }

    @Override
    public List<ToolOptionVO> listTools() {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getStatus, 1)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .orderByAsc(AiTool::getSortNo);
        return aiToolMapper.selectList(wrapper).stream().map(t -> {
            ToolOptionVO vo = new ToolOptionVO();
            vo.setId(t.getId());
            vo.setToolCode(t.getToolCode());
            vo.setToolName(t.getToolName());
            vo.setToolType(t.getToolType());
            return vo;
        }).toList();
    }
}
