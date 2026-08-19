package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.ChangePasswordRequest;
import com.example.aitools.dto.FindAccountRequest;
import com.example.aitools.dto.FindAccountResponse;
import com.example.aitools.dto.LoginRequest;
import com.example.aitools.dto.LoginResponse;
import com.example.aitools.dto.RegisterRequest;
import com.example.aitools.dto.RegisterResponse;
import com.example.aitools.dto.ResetPasswordRequest;
import com.example.aitools.dto.UpdateProfileRequest;
import com.example.aitools.service.UserService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthUtil authUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.register(request);
        return Result.success("注册成功", response);
    }

    @PostMapping("/find-account")
    public Result<FindAccountResponse> findAccount(@Valid @RequestBody FindAccountRequest request) {
        FindAccountResponse response = userService.findAccount(request);
        return Result.success("查询成功", response);
    }

    @GetMapping("/info")
    public Result<LoginResponse.UserInfo> getUserInfo(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        LoginResponse.UserInfo userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        userService.logout(authUtil.getTokenFromRequest(request));
        return Result.success("退出成功", null);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest changeRequest,
                                       HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        userService.changePassword(userId, changeRequest);
        return Result.success("密码修改成功", null);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return Result.success("密码重置成功", null);
    }

    @PostMapping("/update-profile")
    public Result<LoginResponse.UserInfo> updateProfile(@RequestBody UpdateProfileRequest updateRequest,
                                                        HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        LoginResponse.UserInfo userInfo = userService.updateProfile(userId, updateRequest);
        return Result.success("保存成功", userInfo);
    }

}
