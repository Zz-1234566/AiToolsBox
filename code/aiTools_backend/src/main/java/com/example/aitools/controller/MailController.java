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
     * @param request {email, type}  type 支持 register / reset-password
     * @return 触发限流时返回错误提示
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        String email = request.getEmail().trim();
        String type = request.getType();

        // 校验场景类型
        if (!"register".equals(type) && !"reset-password".equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的验证码场景");
        }

        // 生成验证码并发送，限流检查
        boolean allowed = codeService.generateAndSend(type, email, code ->
                mailService.sendVerifyCode(email, code, expireMinutes));

        if (!allowed) {
            throw new BusinessException(ResultCode.CODE_TOO_FREQUENT);
        }
        return Result.success("验证码已发送", null);
    }
}
