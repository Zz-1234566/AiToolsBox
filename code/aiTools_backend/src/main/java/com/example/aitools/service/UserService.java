package com.example.aitools.service;

import com.example.aitools.dto.ChangePasswordRequest;
import com.example.aitools.dto.FindAccountRequest;
import com.example.aitools.dto.FindAccountResponse;
import com.example.aitools.dto.LoginRequest;
import com.example.aitools.dto.LoginResponse;
import com.example.aitools.dto.RegisterRequest;
import com.example.aitools.dto.RegisterResponse;
import com.example.aitools.dto.ResetPasswordRequest;
import com.example.aitools.dto.UpdateProfileRequest;

public interface UserService {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 token 和用户信息）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 注册响应（含生成的账号和用户名）
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    LoginResponse.UserInfo getUserInfo(Long userId);

    /**
     * 通过邮箱查询账号
     *
     * @param request 查询请求（含邮箱）
     * @return 账号和用户名
     */
    FindAccountResponse findAccount(FindAccountRequest request);

    /**
     * 退出登录：清除内存中的 token 记录
     *
     * @param token 请求携带的 token
     */
    void logout(String token);

    /**
     * 修改密码
     *
     * @param userId  用户ID
     * @param request 旧密码 + 新密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 更新个人资料（用户名/头像）
     *
     * @param userId  用户ID
     * @param request 待更新的字段
     * @return 更新后的用户信息
     */
    LoginResponse.UserInfo updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 忘记密码：校验账号与验证码后重置密码
     *
     * @param request 账号 + 验证码 + 新密码
     */
    void resetPassword(ResetPasswordRequest request);
}
