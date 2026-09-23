package com.example.aitools.config;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * P0-A2 终极修复：全局鉴权拦截器
 * <p>
 * 解决"Controller 忘记调 authUtil.getUserIdFromRequest → NPE"的根问题：
 *   - JwtAuthFilter 已解析 token 并把 userId 写入 request attribute
 *   - 本 Interceptor 校验 attribute 是否存在
 *   - 不存在 → 直接 401 JSON 返回，请求**根本不会到 Controller**
 *   - 存在 → 放行
 * <p>
 * 白名单（{@link Constants#PUBLIC_PATH_PATTERNS}）内端点不要求登录。
 * <p>
 * 新加 Controller 端点**不需要写任何鉴权代码**——只要不在白名单就自动鉴权。
 * 这才是真正的"忘了也安全"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // OPTIONS 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开路径：放行
        if (Constants.PUBLIC_PATH_PATTERNS.contains(path)) {
            return true;
        }

        // 校验 attribute：JwtAuthFilter 已解析 token 并写入 userId
        Object userIdAttr = request.getAttribute(Constants.REQ_ATTR_USER_ID);
        if (userIdAttr instanceof Long) {
            return true;
        }

        // 未登录或 token 无效
        log.debug("AuthInterceptor blocked: path={}, hasUserIdAttr={}", path, userIdAttr != null);
        writeUnauthorized(response, path);
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 修正（P0 编译错）：Result 类只提供 fail(...) 静态工厂方法，没有 error(...)
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
