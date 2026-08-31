package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.CosConfig;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.FileStorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 腾讯云 COS 文件存储实现。
 * 配置 cos.enabled=true 并填入 secret-id/secret-key/region/bucket 后启用。
 * 文件按前缀目录上传（如 avatar/uuid.jpg），上传后设置公开读权限，
 * 返回 COS 默认域名下的公开访问 URL：https://{bucket}.cos.{region}.myqcloud.com/{key}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cos.enabled", havingValue = "true")
public class CosFileStorageService implements FileStorageService {

    private final CosConfig cosConfig;

    private COSClient cosClient;

    @PostConstruct
    public void init() {
        if (isBlank(cosConfig.getSecretId()) || isBlank(cosConfig.getSecretKey())
                || isBlank(cosConfig.getRegion()) || isBlank(cosConfig.getBucket())) {
            throw new IllegalStateException("cos.enabled=true 但 COS 配置不完整，请补充 secret-id/secret-key/region/bucket");
        }
        COSCredentials credentials = new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
        this.cosClient = new COSClient(credentials, clientConfig);
        log.info("COS file storage initialized, bucket={}, region={}", cosConfig.getBucket(), cosConfig.getRegion());
    }

    @Override
    public FileUploadResponse store(MultipartFile file, String prefix) {
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = FileStorageService.extractExtension(originalFilename);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(prefix, fileId, ext);
        boolean isPrivate = FileStorageService.isPrivatePrefix(prefix);
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }
            cosClient.putObject(new PutObjectRequest(cosConfig.getBucket(), key, in, metadata));
            // 公开前缀：setObjectAcl 设为 PublicRead，URL 可直接访问
            // 私有前缀：不 setAcl，默认私有读，URL 必须带签名
            if (!isPrivate) {
                try {
                    cosClient.setObjectAcl(cosConfig.getBucket(), key, CannedAccessControlList.PublicRead);
                } catch (Exception aclEx) {
                    // P2-B12: put 成功但 setAcl 失败 → 文件保持私有但 URL 公开，访问会 403
                    // 修复：删掉这个文件 + 抛错，避免返回"看似可用但实际 403"的 URL
                    log.error("setObjectAcl failed after put, deleting orphan key={}", key, aclEx);
                    try {
                        cosClient.deleteObject(cosConfig.getBucket(), key);
                    } catch (Exception delEx) {
                        log.error("Failed to delete orphan after setAcl failure: key={}", key, delEx);
                    }
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "文件上传失败（权限设置异常），请重试");
                }
            }
        } catch (IOException e) {
            log.error("Failed to store file to COS: key={}", key, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "文件上传失败，请重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error storing to COS: key={}", key, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "文件上传失败，请重试");
        }
        log.info("File stored to COS: {} -> {} (private={})", originalFilename, key, isPrivate);
        String fileUrl = buildAccessUrl(key, isPrivate);
        return new FileUploadResponse(fileId, fileUrl, originalFilename);
    }

    /**
     * 构造访问 URL：
     * - 公开：直链
     * - 私有：生成 cosClient.generatePresignedUrl 临时签名 URL
     */
    private String buildAccessUrl(String key, boolean isPrivate) {
        String baseUrl = "https://" + cosConfig.getBucket() + ".cos." + cosConfig.getRegion() + ".myqcloud.com/" + key;
        if (!isPrivate) {
            return baseUrl;
        }
        // 私有：生成临时签名 URL
        long ttl = cosConfig.getSignedUrlTtlSeconds() == null ? 300L : cosConfig.getSignedUrlTtlSeconds();
        java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + ttl * 1000L);
        com.qcloud.cos.model.GeneratePresignedUrlRequest req =
                new com.qcloud.cos.model.GeneratePresignedUrlRequest(cosConfig.getBucket(), key);
        req.setExpiration(expiration);
        req.setMethod(com.qcloud.cos.http.HttpMethodName.GET);
        return cosClient.generatePresignedUrl(req).toString();
    }

    /**
     * 拼接 COS 对象键：{prefix}/{fileId}{ext}（prefix 为空则放根目录）
     */
    private String buildKey(String prefix, String fileId, String ext) {
        String normalized = FileStorageService.normalizePrefix(prefix);
        return normalized.isEmpty() ? fileId + ext : normalized + "/" + fileId + ext;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
