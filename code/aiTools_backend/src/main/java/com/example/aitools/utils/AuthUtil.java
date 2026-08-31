package com.example.aitools.utils;

import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 认证工具类：从 JwtAuthFilter 写入的 request attribute 读取当前登录用户。
 * <p>
 * P0-A2 改造：原本每个 Controller 自己从 Authorization 头解析 token（重复 4 处）。
 * 现在 JwtAuthFilter 提前解析，AuthUtil 直接读 attribute（已校验过）。
 * <p>
 * 兼容：attribute 缺失（Filter 未生效或白名单端点）时仍能走老的"从 header 解析"路径。
 */
@Component
@RequiredArgsConstructor
public class AuthUtil {

    private final JwtUtil jwtUtil;

    /**
     * 获取当前登录用户 ID
     * - 优先从 request attribute 读（P0-A2 后 JwtAuthFilter 写入）
     * - 缺失则回退到 header 解析（兼容 Filter 未生效的边界场景）
     * - 都没有 → 抛 401
     */
    public Long getUserIdFromRequest(HttpServletRequest request) {
        Object attr = request.getAttribute(Constants.REQ_ATTR_USER_ID);
        if (attr instanceof Long) {
            return (Long) attr;
        }
        // 回退：从 header 解析（Filter 未生效时）
        String token = getTokenFromRequest(request);
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * 从请求头中提取原始 token（去掉 Bearer 前缀）
     */
    public String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(Constants.TOKEN_HEADER);
        if (authHeader == null || !authHeader.startsWith(Constants.TOKEN_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return authHeader.substring(Constants.TOKEN_PREFIX.length());
    }
}
