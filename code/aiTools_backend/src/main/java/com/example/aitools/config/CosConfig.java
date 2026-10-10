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

    /** 默认头像在桶内的 key 路径（如 avatar/defaultAvatar.png），URL 由 bucket + region 拼出 */
    // 必须是桶里真实存在的对象名：写错（历史上 env 里误成 defaultAvator.png，缺 r）
    // 会让新注册用户的默认头像指向 404，前端 <image> 破图且无 @error 兜底。
    private String defaultAvatarKey = "avatar/defaultAvatar.png";

    /**
     * 私有文件签名 URL 有效期（秒）。仅 file/ 前缀下的用户文件区使用。
     * <p>
     * 默认 1 小时：签名 URL 在前端可能被「点开 → 试听/预览 → 再点下载」多步使用，
     * 5 分钟太短（用户还没看完就 403）。1 小时兼顾可用性与泄露风险窗口。
     * 查询历史记录时会实时重新签发，因此本值只影响单次会话内的可用时长。
     */
    private Integer signedUrlTtlSeconds = 3600;
}
