package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {

    private String token;

    private UserInfo userInfo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo implements Serializable {
        private Long id;
        private String account;
        private String username;
        private String avatar;
        /** 用户角色：admin管理员 / user普通用户（前端据此显隐管理入口） */
        private String role;
    }
}
