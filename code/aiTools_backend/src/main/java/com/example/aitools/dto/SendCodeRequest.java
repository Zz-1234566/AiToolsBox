package com.example.aitools.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 验证码场景：register（注册）/ reset-password（忘记密码重置）
     */
    @NotBlank(message = "场景类型不能为空")
    private String type;
}
