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

    /**
     * 吊销该用户「失效时间点及之前」签发的全部 token（改密 / 重置密码后调用）。
     * <p>
     * 与 {@link #blacklist} 语义不同，两者并存、互不替代：
     * <ul>
     *   <li>黑名单：吊销**单个** jti（登出，O(1)，稀疏存储）</li>
     *   <li>本方法：声明「某时刻之前签发的全部作废」（改密，O(1)，与设备数无关）</li>
     * </ul>
     * 存量 token 的 iat 必然早于该时刻，因此**无需迁移**即全部失效。
     *
     * @param userId            用户 ID
     * @param beforeEpochSecond 失效时间点（epoch **秒**，必须与 JWT iat 同单位）
     */
    void invalidateAllBefore(Long userId, long beforeEpochSecond);

    /**
     * 查询该用户的 token 失效时间点（epoch 秒）。
     *
     * @return 该用户「此前签发的 token 已全部作废」的时间点；无记录返回 0（表示无此限制）
     */
    long getInvalidBefore(Long userId);

    /**
     * 判断某个签发时间的 token 是否已被改密作废（失效判定规则的唯一实现）。
     * <p>
     * <b>边界</b>：用 {@code issuedAt <= invalidBefore} 判定失效。JWT 的 {@code iat} 单位是秒，
     * 精度低于改密时刻的毫秒精度；同一秒内「签发旧 token」与「改密」两个动作的秒值可能相等，
     * 用 {@code <} 会让这个旧 token 逃过判定。取不到 iat（{@code < 0}）时按失效处理（fail-closed）。
     *
     * @param userId          用户 ID
     * @param issuedAtSeconds token 的签发时间（epoch 秒，取不到传 -1）
     * @return 该 token 是否已作废
     */
    boolean isInvalidated(Long userId, long issuedAtSeconds);
}
