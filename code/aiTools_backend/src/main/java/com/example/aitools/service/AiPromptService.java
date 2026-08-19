package com.example.aitools.service;

import com.example.aitools.dto.PromptRequest;
import com.example.aitools.vo.PromptVO;
import com.example.aitools.vo.ToolOptionVO;

import java.util.List;

public interface AiPromptService {

    /** 查询用户指定工具下的提示词 */
    List<PromptVO> list(Long userId, String toolCode);

    /** 新增提示词 */
    Long add(Long userId, PromptRequest request);

    /** 更新提示词（校验归属） */
    void update(Long id, Long userId, PromptRequest request);

    /** 删除提示词（逻辑删除，校验归属） */
    void delete(Long id, Long userId);

    /** 查询已启用工具列表（前端按 tool_type 分组展示） */
    List<ToolOptionVO> listTools();
}
