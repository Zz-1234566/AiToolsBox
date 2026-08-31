package com.example.aitools.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateProfileRequest implements Serializable {

    /**
     * 用户名（可选，不传则不修改）
     * P2-B5: 加 @Size 约束 + 长度限制（sys_user.username VARCHAR(32)）
     */
    @Size(max = 32, message = "用户名长度不能超过 32 字符")
    private String username;

    /**
     * 头像URL（可选，不传则不修改）
     * P2-B5: 加 @Size 约束（sys_user.avatar VARCHAR(255)）
     */
    @Size(max = 255, message = "头像 URL 长度不能超过 255 字符")
    private String avatar;
}
