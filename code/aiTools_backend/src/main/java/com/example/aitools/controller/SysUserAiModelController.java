package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.UserAiModelDTO;
import com.example.aitools.service.SysUserAiModelService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户 AI 模型 BYOK Controller。
 * <p>
 * 仅当前用户可读写自己的 key；服务端只返前缀、明文 Key 仅在写入时接收。
 */
@Slf4j
@RestController
@RequestMapping("/api/user-ai-model")
@RequiredArgsConstructor
public class SysUserAiModelController {

    private final SysUserAiModelService service;
    private final AuthUtil authUtil;

    @PostMapping("/save")
    public Result<UserAiModelDTO> save(@Valid @RequestBody UserAiModelDTO dto,
                                       HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success("保存成功", service.save(userId, dto));
    }

    @GetMapping("/list")
    public Result<List<UserAiModelDTO>> list(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(service.listByUser(userId));
    }

    @PostMapping("/set-default/{id}")
    public Result<Void> setDefault(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        service.setDefault(userId, id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        service.remove(userId, id);
        return Result.success();
    }
}