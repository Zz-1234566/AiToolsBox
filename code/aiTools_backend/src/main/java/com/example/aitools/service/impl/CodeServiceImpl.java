package com.example.aitools.service.impl;

import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.CodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务：生成、存储、校验验证码（Redis 实现）
 * 实现 CodeService 接口。
 */
@Slf4j  // 修正（P0 编译错）：P1 阶段加了 log.warn 但忘了加 @Slf4j
@Service
public class CodeServiceImpl implements CodeService {

    private final StringRedisTemplate redisTemplate;

    @Value("${verify-code.expire-minutes:5}")
    private int expireMinutes;

    @Value("${verify-code.resend-seconds:60}")
    private int resendSeconds;

    @Value("${verify-code.length:6}")
    private int codeLength;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "aitoolbox:verify:code:";
    private static final String SEND_FLAG_PREFIX = "aitoolbox:verify:sent:";
    private static final String FAIL_COUNT_PREFIX = "aitoolbox:verify:fail:";

    public CodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean generateAndSend(String type, String target, java.util.function.Consumer<String> sendAction) {
        // 场景白名单校验（业务下沉：Controller 不再硬编码 register/reset-password）
        if (type == null || !Constants.CODE_TYPE_ALLOWED.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的验证码场景");
        }
        // 限流检查：60秒内同一 target+type 只能发一次
        String sentFlag = SEND_FLAG_PREFIX + type + ":" + target;
        Boolean sent = redisTemplate.hasKey(sentFlag);
        if (Boolean.TRUE.equals(sent)) {
            return false;
        }

        // 生成验证码
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        String code = sb.toString();

        // 存 Redis，5分钟过期
        String key = CODE_KEY_PREFIX + type + ":" + target;
        redisTemplate.opsForValue().set(key, code, expireMinutes, TimeUnit.MINUTES);

        // 记录发送标记（60秒限流），比验证码过期时间短
        redisTemplate.opsForValue().set(sentFlag, "1", resendSeconds, TimeUnit.SECONDS);

        // 执行实际发送（发邮件）
        sendAction.accept(code);
        return true;
    }

    @Override
    public boolean verify(String type, String target, String inputCode) {
        if (type == null || target == null) {
            return false;
        }
        if (inputCode == null || inputCode.isBlank()) {
            incrementFailCount(type, target);
            return false;
        }
        // 1) 失败计数已达上限 → 锁定中，直接拒绝
        String failKey = FAIL_COUNT_PREFIX + type + ":" + target;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= Constants.VERIFY_CODE_MAX_FAIL_COUNT) {
            log.warn("Verify code locked due to too many failures: type={}, target={}", type, target);
            return false;
        }
        // 2) 校验验证码
        String key = CODE_KEY_PREFIX + type + ":" + target;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            incrementFailCount(type, target);
            return false;
        }
        if (storedCode.equals(inputCode.trim())) {
            // 验证通过：清除失败计数 + 一次性消费验证码（防止同一验证码被多次使用）
            // P0 用户反馈：delete 之前由调用方记得调，易遗漏。改在 verify 内部完成
            redisTemplate.delete(failKey);
            redisTemplate.delete(key);
            return true;
        }
        // 验证失败：累加计数器
        incrementFailCount(type, target);
        return false;
    }

    /**
     * 累加失败次数，达到上限后由 Redis TTL 自然过期实现锁定
     */
    private void incrementFailCount(String type, String target) {
        String failKey = FAIL_COUNT_PREFIX + type + ":" + target;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, Constants.VERIFY_CODE_FAIL_LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public void delete(String type, String target) {
        String key = CODE_KEY_PREFIX + type + ":" + target;
        redisTemplate.delete(key);
    }
}
