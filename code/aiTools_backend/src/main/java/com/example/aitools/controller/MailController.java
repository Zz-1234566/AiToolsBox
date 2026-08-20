package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.SendCodeRequest;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.CodeService;
import com.example.aitools.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final CodeService codeService;
    private final MailService mailService;

    @Value("${verify-code.expire-minutes:5}")
    private int expireMinutes;

    /**
     * 发送邮箱验证码
     * @param request {email, type}  type 取自 Constants.CODE_TYPE_* 白名单
     * @return 触发限流时返回错误提示
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        String email = request.getEmail().trim();
        // 场景白名单校验下沉到 CodeService.generateAndSend（不在 Controller 硬编码 register/reset-password）

        // 生成验证码并发送，限流检查
        boolean allowed = codeService.generateAndSend(request.getType(), email, code ->
                mailService.sendVerifyCode(email, code, expireMinutes));

        if (!allowed) {
            throw new BusinessException(ResultCode.CODE_TOO_FREQUENT);
        }
        return Result.success("验证码已发送", null);
    }
}
