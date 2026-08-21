package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cos")
public class CosConfig {

    /** 是否启用腾讯云 COS 存储（false 时使用本地存储） */
    private Boolean enabled = false;

    /** 腾讯云 SecretId */
    private String secretId;

    /** 腾讯云 SecretKey */
    private String secretKey;

    /** COS 地域，如 ap-guangzhou */
    private String region;

    /** COS Bucket，如 example-1250000000 */
    private String bucket;

    /** 默认头像在桶内的 key 路径（如 avatar/defaultAvator.png），URL 由 bucket + region 拼出 */
    private String defaultAvatarKey = "avatar/defaultAvator.png";
}
