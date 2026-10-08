package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.DocToTextResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.DocToTextService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档转文本 Controller（工作流节点 / 独立工具入口共用）。
 * <p>
 * 只做：接收文件 + 鉴权 + 调 service；纯解析不调用 AI，因此无提示词参数。
 * 与「AI 文件解读」的区别：本端点只抽取文字层，不做 AI 解读，输出可直接作为下游文本节点的输入。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-office")
@RequiredArgsConstructor
public class DocToTextController {

    private final DocToTextService docToTextService;
    private final AuthUtil authUtil;

    @PostMapping(value = "/doc-to-text", consumes = "multipart/form-data")
    public Result<DocToTextResponse> docToText(@RequestParam("file") MultipartFile file,
                                               HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.DOC_EMPTY.getCode(), "请选择要转换的文档");
        }
        return Result.success("转换成功", docToTextService.toText(userId, file));
    }
}
