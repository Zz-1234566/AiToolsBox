package com.example.aitools.config;

import com.example.aitools.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;
    private final FileConfig fileConfig;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // P0-A2 终极修复：注册 AuthInterceptor
        // JwtAuthFilter 写 userId attribute；AuthInterceptor 校验 attribute 是否存在
        // 不在 PUBLIC_PATH_PATTERNS 的路径全部要求登录
        // 注意：公开的本地静态资源（/uploads/tool-output/**）**不在这里** excludePathPatterns——
        // 拦截器排除按未经归一化的 requestURI 做 Ant 匹配，/uploads/tool-output/../file/1/x.png
        // 会被 ** 匹配上而放行，但静态资源映射随后把路径归一化到真实的 file/1/x.png，
        // 导致用户上传的私有文件可被无 token 读取。
        // 公开判定改在 AuthInterceptor 内对「归一化后的路径」做，见 isPublicLocalPath。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地文件上传目录映射：/uploads/** -> uploads/ 目录
        String uploadDir = Paths.get(fileConfig.getUploadDir()).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
