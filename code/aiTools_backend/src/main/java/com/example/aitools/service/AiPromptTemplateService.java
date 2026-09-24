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

    /**
     * 解析指定用途的提示词：用户自定义优先，其次系统提示词（promptId 选中且用途匹配时），
     * 最后回退到该系统默认提示词；均无时返回 null。
     * <p>供 handler / service 公共调用，不再做私有复制。
     */
    String resolvePrompt(String userProvided, Long promptId, String promptUse, String toolCode);

    /**
     * 按工具编码查工具ID（查不到返回 null）
     */
    Long findToolIdByCode(String toolCode);
}
