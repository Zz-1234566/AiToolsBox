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

        // 公开的本地静态资源（工具产物）：放行
        // 必须先归一化再判断：静态资源映射会在服务端把 ../ 折叠掉，
        // 而 requestURI 保留了原始的 ../，直接用原始串做前缀匹配会被
        // /uploads/tool-output/../file/1/x.png 这类穿越请求绕过，
        // 导致 file/ 下的私有文件被无 token 读走。
        if (isPublicLocalPath(path)) {
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

    /**
     * 是否本地存储模式下的公开静态资源（tool-output/ / avatar/ / ai-image/ / ai-bg/）。
     * <p>
     * 先把 URI 归一化（折叠 {@code .} / {@code ../}、解码 %xx）再做前缀匹配，
     * 与静态资源映射解析出的真实文件路径保持同一套语义，
     * 避免 {@code /uploads/avatar/../file/1/x.png} 这类穿越请求
     * 先被前缀放行、再由静态映射读到 file/ 下的私有文件。
     */
    private boolean isPublicLocalPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = normalize(path);
        if (normalized == null) {
            return false;
        }
        for (String prefix : Constants.PUBLIC_LOCAL_PATH_PREFIXES) {
            // 每个公开前缀都以 / 结尾，startsWith 不会误伤 /uploads/avatarxxx
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 归一化 URI；解码或折叠异常时返回 null（按不公开处理） */
    private String normalize(String path) {
        try {
            String decoded = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
            // 统一分隔符，避免 %5c 之类把 ../ 伪装成 ..\
            String unified = decoded.replace('\\', '/');
            java.nio.file.Path resolved = java.nio.file.Paths.get(unified).normalize();
            String s = resolved.toString().replace('\\', '/');
            if (!s.startsWith("/")) {
                s = "/" + s;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
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
