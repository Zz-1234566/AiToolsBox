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
import com.example.aitools.service.TokenBlacklistService;
import com.example.aitools.service.UserService;
import com.example.aitools.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.crypto.digest.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
// 原本 P0-A3 阶段使用 ConcurrentHashMap 维护内存 token 黑名单，但全代码库没有任何 .get() 读取该 Map，
// 属于"写不读"的死代码。删除后：JWT 自身 7 天过期 + 客户端 logout 清本地缓存。
// 真要吊销 token：升级方案 A 接 Redis（已配置好 StringRedisTemplate 但未使用）。

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final CodeService codeService;
    private final CosConfig cosConfig;
    private final TokenBlacklistService tokenBlacklistService;

    // P0-A3 删除：原内存 tokenStore（写不读的死代码）
    // private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 默认头像 URL：从 CosConfig 拼出（bucket + region + defaultAvatarKey，文件 avatar/defaultAvatar.png） */
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

        // P0-A3 删除：原 tokenStore.put(token, user.getId())
        // 改：仅依赖 JWT 自身 7 天过期，不维护内存黑名单
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
        // 验证码一次性消费：P0 用户反馈后已移到 CodeServiceImpl.verify 内部自动 delete

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
        user.setRole(Constants.ROLE_USER);
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
        // P2-B8: 真实吊销 token — 把 jti 写 Redis 黑名单
        if (token == null || token.isBlank()) {
            return;
        }
        String jti = jwtUtil.getJtiFromToken(token);
        long remaining = jwtUtil.getRemainingMillis(token);
        if (jti != null && remaining > 0) {
            tokenBlacklistService.blacklist(jti, remaining);
        }
        log.info("User logged out, token jti={} blacklisted for {}ms", jti, remaining);
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
        revokeTokensIssuedBeforeNow(userId);
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

        // 账号不存在：模拟"做了事"的耗时再返回，避免时间侧信道枚举
        // 仍走成功路径，不抛 USER_NOT_FOUND
        if (user == null) {
            log.info("Reset password attempt for non-existent account: account={}", request.getAccount());
            fakeWorkToPreventTimingLeak();
            return;
        }

        // 校验验证码（用账号绑定邮箱做 target）
        if (!codeService.verify(com.example.aitools.common.Constants.CODE_TYPE_RESET_PASSWORD, user.getEmail(), request.getCode())) {
            log.info("Reset password failed: wrong code for account={}", request.getAccount());
            fakeWorkToPreventTimingLeak();
            return;
        }
        // 验证码一次性消费：P0 用户反馈后已移到 CodeServiceImpl.verify 内部自动 delete

        // 更新密码
        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        userMapper.updateById(user);
        // 重置密码等价于改密：同样要吊销已签发的 token，否则密码泄露后旧 token 仍可用 7 天
        revokeTokensIssuedBeforeNow(user.getId());
        log.info("User password reset: account={}", request.getAccount());
    }

    /**
     * 吊销该用户在「当前时刻」及之前签发的全部 token（改密 / 重置密码后调用）。
     * <p>
     * 用「失效时间点」而非逐个 jti 拉黑：用户可能同时在手机/电脑/平板登录，
     * 逐个吊销需要枚举全部活跃 token，而 Redis 里并没有维护这份集合。
     * 声明时间点后，改密前的存量 token 天然（iat 早于该时刻）全部失效，无需迁移。
     * <p>
     * 单位必须是**秒**（与 JWT {@code iat} claim 同单位），用毫秒会导致比较恒不成立。
     */
    private void revokeTokensIssuedBeforeNow(Long userId) {
        tokenBlacklistService.invalidateAllBefore(userId, System.currentTimeMillis() / 1000L);
    }

    /**
     * 模拟"做了事"的耗时：BCrypt 一轮约 50-100ms，与正常路径耗时相当，避免攻击者通过响应时间区分账号是否存在
     */
    private void fakeWorkToPreventTimingLeak() {
        BCrypt.hashpw("timing-decoy-" + System.nanoTime(), BCrypt.gensalt());
    }

    /**
     * 生成唯一账号：AIT + N位随机数字（N 默认 8，用户量大时自动扩到 9）
     * <p>
     * 9 位封顶原因（P0 用户反馈）：
     *   - 9 位：(int) Math.pow(10, 9) = 1_000_000_000 ≈ 10 亿，< Integer.MAX_VALUE (≈ 21 亿) — 安全
     *   - 10 位：(int) Math.pow(10, 10) = 10_000_000_000 已超 Integer.MAX_VALUE
     *           Math.pow 返回 double，强转 int 时精度丢失，可能出现负数
     *   - 9 位账号空间 = 10 亿，足覆盖毕设项目（即便 1 亿用户也只在 1/10 命中率）
     *   - 真要支持 10 位：改用 long + String.format 模式（"%" + digitCount + "d"）
     */
    private String generateUniqueAccount() {
        // 降级路径：100 次冲突（碰撞率约 1% 时）就扩一位，从 1 亿 → 10 亿组合
        int digitCount = Constants.ACCOUNT_DIGIT_COUNT;  // 8
        for (int attempt = 0; attempt < 2; attempt++) {
            int range = (int) Math.pow(10, digitCount);
            int maxAttempts = 100;
            for (int i = 0; i < maxAttempts; i++) {
                String account = Constants.ACCOUNT_PREFIX
                        + String.format("%0" + digitCount + "d", RANDOM.nextInt(range));
                LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(User::getAccount, account)
                        .eq(User::getDr, Constants.DR_NORMAL);
                if (userMapper.selectCount(wrapper) == 0) {
                    return account;
                }
            }
            // 8 位碰撞率高，降级到 9 位
            log.warn("Account generation collided {} times with {}-digit numbers, upgrading to {}-digit",
                    maxAttempts, digitCount, digitCount + 1);
            digitCount++;
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
        userInfo.setRole(user.getRole() == null ? Constants.ROLE_USER : user.getRole());
        return userInfo;
    }
}
