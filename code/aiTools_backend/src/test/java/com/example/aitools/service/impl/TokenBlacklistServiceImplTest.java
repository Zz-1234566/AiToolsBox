package com.example.aitools.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 改密后 token 全量失效的判定规则测试（缺陷 1）。
 * <p>
 * 只测纯逻辑（{@code isInvalidated}），Redis 用 mock 顶替：
 * 重点覆盖**同一秒边界**——JWT 的 iat 单位是秒，精度低于改密时刻的毫秒精度，
 * 若判定写成 {@code iat < invalidBefore}，同一秒内签发的旧 token 会逃过失效。
 */
class TokenBlacklistServiceImplTest {

    private static final String KEY_PREFIX = "aitoolbox:token:invalidBefore:";
    private static final Long USER_ID = 42L;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private TokenBlacklistServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TokenBlacklistServiceImpl(redisTemplate);
    }

    /** 让 Redis 里存在「某秒之前签发的 token 全部作废」这一条记录 */
    private void givenInvalidBefore(String value) {
        when(valueOps.get(KEY_PREFIX + USER_ID)).thenReturn(value);
    }

    @Test
    @DisplayName("从未改密：任何 token 都不受影响")
    void notChangedPassword_neverInvalidates() {
        givenInvalidBefore(null);

        assertFalse(service.isInvalidated(USER_ID, 1000L), "无失效记录时应放行");
    }

    @Test
    @DisplayName("改密前签发的 token → 判定失效")
    void tokenIssuedBeforeChange_isInvalidated() {
        givenInvalidBefore("2000");

        assertTrue(service.isInvalidated(USER_ID, 1999L), "早于失效时间点必须作废");
    }

    @Test
    @DisplayName("同一秒边界：iat 与失效时刻相等 → 仍判定失效")
    void sameSecondBoundary_isInvalidated() {
        givenInvalidBefore("2000");

        assertTrue(service.isInvalidated(USER_ID, 2000L),
                "iat 单位为秒，同一秒签发的旧 token 必须一并作废（用 < 判定会漏）");
    }

    @Test
    @DisplayName("改密后重新登录的新 token → 放行")
    void tokenIssuedAfterChange_isAllowed() {
        givenInvalidBefore("2000");

        assertFalse(service.isInvalidated(USER_ID, 2001L), "失效时间点之后签发的 token 应可用");
    }

    @Test
    @DisplayName("取不到 iat（如老 token 无该 claim）→ fail-closed 按失效处理")
    void missingIat_isInvalidated() {
        givenInvalidBefore("2000");

        assertTrue(service.isInvalidated(USER_ID, -1L), "解析不出签发时间时不能放行");
    }

    @Test
    @DisplayName("非法值 → 视为无限制，不得因脏数据把用户全部锁在门外")
    void corruptedValue_isTreatedAsNoRestriction() {
        givenInvalidBefore("not-a-number");

        assertEquals(0L, service.getInvalidBefore(USER_ID));
        assertFalse(service.isInvalidated(USER_ID, 1L));
    }

    @Test
    @DisplayName("Redis 故障 → fail-open，不得引发全体 401")
    void redisDown_failsOpen() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertFalse(service.isInvalidated(USER_ID, 1L), "Redis 抖动不应导致全体用户被锁出");
    }

    @Test
    @DisplayName("写入使用秒为单位的 epoch，且 TTL 与 JWT 7 天对齐")
    void invalidateAllBefore_writesEpochSeconds() {
        service.invalidateAllBefore(USER_ID, 1_700_000_000L);

        verify(valueOps).set(KEY_PREFIX + USER_ID, "1700000000", 7L, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("改密只写 1 个 key，与设备数无关")
    void invalidateAllBefore_isO1_regardlessOfDeviceCount() {
        service.invalidateAllBefore(USER_ID, 1_700_000_000L);

        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(valueOps, never()).get(anyString());
    }
}