package com.example.aitools.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),

    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "用户已被禁用"),
    PASSWORD_MISMATCH(1005, "两次密码不一致"),
    EMAIL_ALREADY_EXISTS(1006, "邮箱已被使用"),

    // 参数相关 2xxx
    PARAM_ERROR(2001, "参数错误"),
    PARAM_MISSING(2002, "缺少必要参数"),
    CODE_ERROR(2003, "验证码错误或已过期"),
    CODE_TOO_FREQUENT(2004, "发送太频繁，请稍后再试"),

    // 系统相关 9xxx
    SYSTEM_ERROR(9001, "系统内部错误"),
    SERVICE_UNAVAILABLE(9002, "服务不可用");

    private final int code;
    private final String message;
}
