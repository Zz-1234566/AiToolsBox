package com.example.aitools.config;

import com.example.aitools.common.Constants;
import com.example.aitools.service.TokenBlacklistService;
import com.example.aitools.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * JWT 鉴权 Filter：每个请求解析一次 Authorization 头，校验通过把 userId 塞进 request attribute。
 * <p>
 * 解决 AGENTS.md 标记的 P0-A2 问题：原本每个 Controller 自己调 authUtil.getUserIdFromRequest，
 * 未来新加端点忘记调即裸奔。Filter 统一拦截后：
 * - 不需要登录的端点：白名单放行（attribute 不设）
 * - 需要登录的端点：attribute 必须存在 → AuthUtil 抛 401
 * <p>
 * Filter 本身不拦截，**是否强制登录**由调用方决定（保留 Controller 层调 authUtil.getUserIdFromRequest 的行为）。
 * 这样新加端点只要调 authUtil.getUserIdFromRequest 就自动走 401 流程，
 * 但不调也不会"裸奔"——只是 attribute 为 null 后续可能 NPE。
 * <p>
 * 真要彻底拦截：在 addInterceptors 注册一个 HandlerInterceptor，attribute 不存在直接 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(Constants.TOKEN_HEADER);
        if (authHeader != null && authHeader.startsWith(Constants.TOKEN_PREFIX)) {
            String token = authHeader.substring(Constants.TOKEN_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    Claims claims = jwtUtil.parseToken(token);
                    if (claims != null) {
                        // P2-B8: 检查 jti 是否在黑名单（logout 真实吊销）
                        String jti = claims.getId();
                        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                            log.debug("Token blacklisted: jti={}", jti);
                            // token 已被吊销：不设 attribute，依赖调用方 authUtil 抛 401
                        } else if (isStaleAfterPasswordChange(claims)) {
                            // 改密/重置密码后：该时刻及之前签发的 token 全部作废（多设备同时失效）
                            // 不设 attribute → AuthInterceptor 直接 401
                        } else {
                            Object userIdObj = claims.get("userId");
                            if (userIdObj != null) {
                                Long userId = Long.valueOf(userIdObj.toString());
                                request.setAttribute(Constants.REQ_ATTR_USER_ID, userId);
                            }
                        }
                    }
                } catch (Exception e) {
                    // token 无效：attribute 不设，依赖调用方 authUtil 抛 401。
                    // 用 warn 而非 debug：debug 在生产（INFO 级）不可见，
                    // 会导致伪造/过期 token 尝试完全无法审计
                    log.warn("JWT 解析失败（不设置登录态）: {}", e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 改密 / 重置密码后的 token 全量失效校验。
     * <p>
     * 语义：Redis 里记录「该用户从 {@code invalidBefore} 秒起签发的 token 才有效」，
     * 与 jti 黑名单并存（黑名单吊销单个、本方法吊销该时间点之前的全部）。
     * <p>
     * <b>边界处理</b>：失效判定规则（{@code iat <= invalidBefore}、取不到 iat 按失效处理）
     * 统一收敛在 {@link TokenBlacklistService#isInvalidated}，此处只做取值与日志。
     */
    private boolean isStaleAfterPasswordChange(Claims claims) {
        Object userIdObj = claims.get("userId");
        if (userIdObj == null) {
            return false;
        }
        Long userId = Long.valueOf(userIdObj.toString());
        // iat 是 epoch 秒（JWT 规范单位），与 Redis 里记录的失效时间点同单位
        Date issuedAt = claims.getIssuedAt();
        long issuedAtSeconds = issuedAt == null ? -1L : issuedAt.getTime() / 1000L;
        if (tokenBlacklistService.isInvalidated(userId, issuedAtSeconds)) {
            log.info("Token invalidated by password change: userId={}, iat={}s", userId, issuedAtSeconds);
            return true;
        }
        return false;
    }
}
