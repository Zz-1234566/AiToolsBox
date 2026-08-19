package com.example.aitools.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务：生成、存储、校验验证码（Redis 实现）
 * key 格式：verify:code:{type}:{target}
 * value：6 位数字验证码
 * TTL：5 分钟自动过期
 */
@Service
public class CodeService {

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

    public CodeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成验证码并发送（含限流检查）
     * @param type 场景类型（register / reset-password）
     * @param target 目标（邮箱）
     * @param sendAction 实际发送动作（发邮件）
     * @return 是否允许发送（false=触发限流）
     */
    public boolean generateAndSend(String type, String target, java.util.function.Consumer<String> sendAction) {
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

    /**
     * 校验验证码
     * @param type 场景类型
     * @param target 目标（邮箱）
     * @param inputCode 用户输入验证码
     * @return 是否匹配
     */
    public boolean verify(String type, String target, String inputCode) {
        if (inputCode == null || inputCode.isBlank()) {
            return false;
        }
        String key = CODE_KEY_PREFIX + type + ":" + target;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            return false;
        }
        return storedCode.equals(inputCode.trim());
    }

    /**
     * 校验通过后删除验证码（一次性使用）
     */
    public void delete(String type, String target) {
        String key = CODE_KEY_PREFIX + type + ":" + target;
        redisTemplate.delete(key);
    }
}
