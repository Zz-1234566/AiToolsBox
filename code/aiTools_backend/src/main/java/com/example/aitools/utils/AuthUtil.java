package com.example.aitools.utils;

import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 认证工具类：从请求头解析当前登录用户
 */
@Component
@RequiredArgsConstructor
public class AuthUtil {

    private final JwtUtil jwtUtil;

    /**
     * 从请求头中解析用户ID
     */
    public Long getUserIdFromRequest(HttpServletRequest request) {
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
