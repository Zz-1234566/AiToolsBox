package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.PromptGenerateRequest;
import com.example.aitools.dto.PromptRequest;
import com.example.aitools.service.AiPromptService;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.PromptGenerateVO;
import com.example.aitools.entity.AiPrompt;
import com.example.aitools.vo.PromptVO;
import com.example.aitools.vo.SystemPromptVO;
import com.example.aitools.vo.ToolOptionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompt")
@RequiredArgsConstructor
public class AiPromptController {

    private final AiPromptService aiPromptService;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AuthUtil authUtil;

    /**
     * 按工具场景查询该系统所有系统提示词（用于前端下拉选择）
     */
    @GetMapping("/system/list")
    public Result<List<SystemPromptVO>> systemList(@RequestParam String toolCode,
                                                   HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        return Result.success(aiPromptTemplateService.listByTool(toolCode));
    }

    /**
     * 按工具编码取系统 format 用途的默认提示词文本（前端只读展示用）
     * 找不到返回空字符串
     */
    @GetMapping("/system/format")
    public Result<String> systemFormat(@RequestParam String toolCode,
                                       HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        AiPrompt prompt = aiPromptTemplateService.getDefaultByUse(toolCode, "format");
        String text = (prompt == null) ? "" : (prompt.getPromptContent() == null ? "" : prompt.getPromptContent());
        return Result.success(text);
    }

    @GetMapping("/list")
    public Result<List<PromptVO>> list(@RequestParam String toolCode,
                                       HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(aiPromptService.list(userId, toolCode));
    }

    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody PromptRequest promptRequest, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        Long id = aiPromptService.add(userId, promptRequest);
        return Result.success("新增成功", id);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PromptRequest promptRequest,
                               HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        aiPromptService.update(id, userId, promptRequest);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        aiPromptService.delete(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 查询已启用工具列表（前端按 tool_type 分组展示，用于用户提示词绑定工具下拉）
     */
    @GetMapping("/tools")
    public Result<List<ToolOptionVO>> tools(HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request); // 校验登录
        return Result.success(aiPromptService.listTools());
    }

    /**
     * AI 生成提示词：调 DeepSeek 按用户需求生成一段提示词
     * 前端从 tools.js 读出 toolName/toolDesc 传入，无需后端查表
     */
    @PostMapping("/generate")
    public Result<PromptGenerateVO> generate(@Valid @RequestBody PromptGenerateRequest request,
                                              HttpServletRequest httpRequest) {
        authUtil.getUserIdFromRequest(httpRequest); // 校验登录
        return Result.success("生成成功", aiPromptService.generatePrompt(request));
    }
}