package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.config.CosConfig;
import com.example.aitools.dto.ChangePasswordRequest;
import com.example.aitools.dto.FindAccountRequest;
import com.example.aitools.dto.FindAccountResponse;
import com.example.aitools.dto.LoginRequest;
import com.example.aitools.dto.LoginResponse;
import com.example.aitools.dto.RegisterRequest;
import com.example.aitools.dto.RegisterResponse;
import com.example.aitools.dto.ResetPasswordRequest;
import com.example.aitools.dto.UpdateProfileRequest;
import com.example.aitools.entity.User;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.UserMapper;
import com.example.aitools.service.CodeService;
import com.example.aitools.service.UserService;
import com.example.aitools.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.crypto.digest.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final CodeService codeService;
    private final CosConfig cosConfig;

    /** In-memory token store (replace with Redis in production) */
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 默认头像 URL：从 CosConfig 拼出（bucket + region + defaultAvatarKey） */
    private String defaultAvatarUrl() {
        return "https://" + cosConfig.getBucket() + ".cos." + cosConfig.getRegion() + ".myqcloud.com/" + cosConfig.getDefaultAvatarKey();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 根据账号查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getAccount, request.getAccount())
                .eq(User::getDr, Constants.DR_NORMAL);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 校验密码
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getAccount());

        // 存储 token（内存，7天过期由 JWT 自行校验）
        tokenStore.put(token, user.getId());
        log.info("User logged in: account={}", user.getAccount());

        // 构建响应
        return buildLoginResponse(token, user);
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        // 校验邮箱验证码
        if (!codeService.verify(com.example.aitools.common.Constants.CODE_TYPE_REGISTER, request.getEmail().trim(), request.getCode())) {
            throw new BusinessException(ResultCode.CODE_ERROR);
        }
        // 校验通过后删除验证码（一次性使用）
        codeService.delete(com.example.aitools.common.Constants.CODE_TYPE_REGISTER, request.getEmail().trim());

        // 校验两次密码是否一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_MISMATCH);
        }

        // 校验用户名唯一性
        LambdaQueryWrapper<User> nameWrapper = new LambdaQueryWrapper<>();
        nameWrapper.eq(User::getUsername, request.getUsername())
                .eq(User::getDr, Constants.DR_NORMAL);
        if (userMapper.selectCount(nameWrapper) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 校验邮箱唯一性（服务层第一道校验）
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, request.getEmail())
                .eq(User::getDr, Constants.DR_NORMAL);
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 生成唯一账号
        String account = generateUniqueAccount();

        // 创建用户
        User user = new User();
        user.setAvatar(defaultAvatarUrl());
        user.setAccount(account);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setStatus(Constants.USER_STATUS_NORMAL);
        user.setDr(Constants.DR_NORMAL);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 数据库层唯一索引兜底（并发场景下服务层校验可能失效）
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        log.info("User registered: account={}, username={}, email={}", account, request.getUsername(), request.getEmail());

        return new RegisterResponse(account, request.getUsername());
    }

    @Override
    public FindAccountResponse findAccount(FindAccountRequest request) {
        // 根据邮箱查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, request.getEmail())
                .eq(User::getDr, Constants.DR_NORMAL);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        return new FindAccountResponse(user.getAccount(), user.getUsername());
    }

    @Override
    public LoginResponse.UserInfo getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return buildUserInfo(user);
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenStore.remove(token);
        }
        log.info("User logged out, token removed from store");
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 校验旧密码
        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        // 新密码长度校验兜底（正常由 DTO @Size 校验）
        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "新密码长度需在6-20位之间");
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userMapper.updateById(user);
        log.info("User password changed: userId={}", userId);
    }

    @Override
    public LoginResponse.UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 用户名：传了才改，且校验唯一性（排除自己）
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().trim();
            LambdaQueryWrapper<User> nameWrapper = new LambdaQueryWrapper<>();
            nameWrapper.eq(User::getUsername, newUsername)
                    .ne(User::getId, userId)
                    .eq(User::getDr, Constants.DR_NORMAL);
            if (userMapper.selectCount(nameWrapper) > 0) {
                throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
            }
            user.setUsername(newUsername);
        }
        // 头像：传了才改（可为空串，用于清除头像）
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        userMapper.updateById(user);
        log.info("User profile updated: userId={}", userId);
        return buildUserInfo(user);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        // 校验两次密码一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_MISMATCH);
        }

        // 按账号查用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getAccount, request.getAccount())
                .eq(User::getDr, Constants.DR_NORMAL);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 校验验证码（用账号绑定邮箱做 target）
        if (!codeService.verify(com.example.aitools.common.Constants.CODE_TYPE_RESET_PASSWORD, user.getEmail(), request.getCode())) {
            throw new BusinessException(ResultCode.CODE_ERROR);
        }
        codeService.delete(com.example.aitools.common.Constants.CODE_TYPE_RESET_PASSWORD, user.getEmail());

        // 更新密码
        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        userMapper.updateById(user);
        log.info("User password reset: account={}", request.getAccount());
    }

    /**
     * 生成唯一账号：AIT + 8位随机数字
     */
    private String generateUniqueAccount() {
        int maxAttempts = 100;
        for (int i = 0; i < maxAttempts; i++) {
            String account = Constants.ACCOUNT_PREFIX
                    + String.format("%0" + Constants.ACCOUNT_DIGIT_COUNT + "d", RANDOM.nextInt(100000000));
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getAccount, account)
                    .eq(User::getDr, Constants.DR_NORMAL);
            if (userMapper.selectCount(wrapper) == 0) {
                return account;
            }
        }
        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "账号生成失败，请重试");
    }

    private LoginResponse buildLoginResponse(String token, User user) {
        LoginResponse.UserInfo userInfo = buildUserInfo(user);
        return new LoginResponse(token, userInfo);
    }

    private LoginResponse.UserInfo buildUserInfo(User user) {
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setAccount(user.getAccount());
        userInfo.setUsername(user.getUsername());
        userInfo.setAvatar(user.getAvatar());
        return userInfo;
    }
}
