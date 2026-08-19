package com.example.aitools.service;

import com.example.aitools.entity.AiPrompt;
import com.example.aitools.vo.SystemPromptVO;

import java.util.List;

public interface AiPromptTemplateService {

    /**
     * 按工具编码查询该系统所有提示词列表（用于前端选择）
     * @param toolCode 工具编码
     */
    List<SystemPromptVO> listByTool(String toolCode);

    /**
     * 按提示词ID查询（精确一条，查不到抛"提示词不存在"）
     * @param promptId 提示词ID
     */
    AiPrompt getById(Long promptId);

    /**
     * 按工具编码+类型取默认（第一条）
     * @param toolCode 工具编码
     * @param promptType 提示词类型（system/user）
     */
    AiPrompt getDefault(String toolCode, String promptType);

    /**
     * 按工具编码+用途取默认（第一条）
     * @param toolCode 工具编码
     * @param promptUse 提示词用途（format/generate）
     */
    AiPrompt getDefaultByUse(String toolCode, String promptUse);
}
