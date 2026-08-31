package com.example.aitools.service.impl;

import com.example.aitools.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * P2-B8: Redis 实现 token 黑名单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "aitoolbox:token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String jti, long ttlMillis) {
        if (jti == null || jti.isBlank() || ttlMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttlMillis, TimeUnit.MILLISECONDS);
            log.info("Token blacklisted: jti={}, ttl={}ms", jti, ttlMillis);
        } catch (Exception e) {
            // Redis 故障：仅 log，不阻断 logout（前端已经清 token，下次请求会被 Filter 拦住）
            log.error("Failed to blacklist token: jti={}", jti, e);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            Boolean has = redisTemplate.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            // Redis 故障：fail-open（不阻拦）— 优于 fail-closed（所有请求 401 雪崩）
            log.warn("Redis unavailable for blacklist check, failing open: jti={}", jti, e);
            return false;
        }
    }
}
