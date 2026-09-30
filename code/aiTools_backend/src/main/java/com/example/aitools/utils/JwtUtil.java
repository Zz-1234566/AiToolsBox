package com.example.aitools.utils;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.JwtConfig;
import com.example.aitools.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /** 禁止使用的开发默认密钥（与 application.yml 中的 ${JWT_SECRET:default-...} 一致） */
    private static final String[] FORBIDDEN_DEFAULT_SECRETS = {
            "default-dev-secret-key-change-in-production-please-2024",
            "your-jwt-secret-key-please-change-in-production"
    };

    /** JWT 最小安全长度（字节），HS256 要求 ≥ 32 字节 */
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtConfig jwtConfig;

    @PostConstruct
    public void validateSecretOnStartup() {
        String secret = jwtConfig.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 启动失败：未配置 jwt.secret。请设置环境变量 JWT_SECRET（≥32 字节随机字符串）");
        }
        for (String forbidden : FORBIDDEN_DEFAULT_SECRETS) {
            if (forbidden.equals(secret)) {
                throw new IllegalStateException(
                        "JWT 启动失败：检测到开发默认密钥 '" + forbidden + "'。" +
                        "请设置环境变量 JWT_SECRET（≥32 字节随机字符串），不要使用默认值！");
            }
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 启动失败：jwt.secret 长度 " + secret.length() +
                    " 字节 < " + MIN_SECRET_LENGTH + " 字节。HS256 要求 ≥32 字节随机字符串。");
        }
        log.info("JWT secret validated, length={} bytes", secret.length());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     *
     * @param userId  用户ID
     * @param account 账号
     * @return token string
     */
    public String generateToken(Long userId, String account) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("account", account);
        // P2-B8: 加 jti 唯一标识，用于 logout 真实吊销
        String jti = java.util.UUID.randomUUID().toString();
        claims.put("jti", jti);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .id(jti)  // JWT 标准的 jti claim（jjwt 0.12.x 用 .id()）
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 token 中获取 jti（P2-B8 用于 logout 真实吊销）
     */
    public String getJtiFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getId();
        } catch (Exception e) {
            // 原实现静默返回 null：会导致 logout 跳过黑名单（登出实际失效）却仍打印成功日志
            log.warn("解析 token 的 jti 失败，该 token 无法加入黑名单", e);
            return null;
        }
    }

    /**
     * 从 token 中获取剩余有效期（毫秒）
     */
    public long getRemainingMillis(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("解析 token 剩余有效期失败，按已过期处理", e);
            return 0;
        }
    }

    /**
     * 解析 Token
     *
     * @param token JWT token
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户ID
     *
     * @param token JWT token
     * @return userId
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            // token 无效/过期属鉴权失败，必须 401；
            // 若冒泡会被兜底 handler 转成 500「系统内部错误」，前端不触发重新登录且污染监控
            log.warn("token 解析失败（按未登录处理）: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    /**
     * 从 Token 中获取账号
     *
     * @param token JWT token
     * @return account
     */
    public String getAccountFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("account", String.class);
    }

    /**
     * 校验 Token 是否过期
     *
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            // P2-B6: 加 debug 日志，便于排查伪造 token / 过期 / 解析失败
            log.debug("isTokenExpired: parse failed, treat as expired: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 校验 Token 是否有效
     *
     * @param token JWT token
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            // P2-B6: 校验失败时记 warn（不是 debug）— 这是可疑事件
            //   可能场景：伪造 token / 密钥已轮换 / 客户端用了旧 secret
            log.warn("validateToken: parse failed (possible forged or stale token): {}", e.getMessage());
            return false;
        }
    }
}
