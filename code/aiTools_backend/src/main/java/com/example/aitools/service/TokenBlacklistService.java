package com.example.aitools.service;

/**
 * P2-B8: JWT token 真实吊销服务
 * <p>
 * 通过 Redis 存黑名单：logout 时把 jti 写入 Redis（TTL = 剩余有效期），
 * JwtAuthFilter 校验 token 前先查黑名单，存在则 401。
 */
public interface TokenBlacklistService {

    /**
     * 把 token 加入黑名单（logout 时调）
     * @param jti JWT 中的 jti 唯一标识
     * @param ttlMillis 黑名单有效期（毫秒），一般取 token 剩余有效期
     */
    void blacklist(String jti, long ttlMillis);

    /**
     * 检查 jti 是否在黑名单中
     */
    boolean isBlacklisted(String jti);
}
