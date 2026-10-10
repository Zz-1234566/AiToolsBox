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

    /** 全量吊销时间点前缀：aitoolbox:token:invalidBefore:{userId} */
    private static final String INVALID_BEFORE_PREFIX = "aitoolbox:token:invalidBefore:";

    /** 失效时间点 TTL（天），与 jwt.expiration（7 天）对齐：超过该时长的 token 本就过期，标记无意义 */
    private static final long INVALID_BEFORE_TTL_DAYS = 7L;

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

    @Override
    public void invalidateAllBefore(Long userId, long beforeEpochSecond) {
        if (userId == null) {
            return;
        }
        try {
            // TTL 与 JWT 有效期对齐即可：过期后不可能再有过期的 token 通过校验
            redisTemplate.opsForValue().set(INVALID_BEFORE_PREFIX + userId,
                    String.valueOf(beforeEpochSecond), INVALID_BEFORE_TTL_DAYS, TimeUnit.DAYS);
            log.info("All tokens before {}s invalidated for userId={}", beforeEpochSecond, userId);
        } catch (Exception e) {
            // 写失败 = 改密后旧 token 仍可用（安全降级），必须留日志告警
            log.error("Failed to invalidate tokens for userId={}", userId, e);
        }
    }

    @Override
    public long getInvalidBefore(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            String raw = redisTemplate.opsForValue().get(INVALID_BEFORE_PREFIX + userId);
            if (raw == null || raw.isBlank()) {
                return 0L;
            }
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Corrupted invalidBefore value, treating as no restriction: userId={}", userId);
            return 0L;
        } catch (Exception e) {
            // 与 isBlacklisted 一致：fail-open，避免 Redis 抖动导致全体用户 401
            log.warn("Redis unavailable for invalidBefore check, failing open: userId={}", userId, e);
            return 0L;
        }
    }

    @Override
    public boolean isInvalidated(Long userId, long issuedAtSeconds) {
        long invalidBefore = getInvalidBefore(userId);
        if (invalidBefore <= 0) {
            // 从未改密：无此限制
            return false;
        }
        if (issuedAtSeconds < 0) {
            // 取不到 iat：fail-closed，不能因为解析不出签发时间就放行
            log.debug("Token without iat treated as invalidated: userId={}", userId);
            return true;
        }
        // <= 而非 <：iat 单位为秒，同一秒内签发的旧 token 必须一并作废
        return issuedAtSeconds <= invalidBefore;
    }
}
