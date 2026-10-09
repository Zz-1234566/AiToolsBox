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
import java.io.ByteArrayInputStream;
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
        return new FileUploadResponse(fileId, fileUrl, originalFilename, key);
    }

    /**
     * 按 key（或历史遗留的带签名 URL）实时签发一个可访问 URL。
     * <p>
     * 查询历史记录时读出的都是库内旧值（可能早已过期的签名 URL），
     * 统一先 {@link FileStorageService#toObjectKey} 剥掉旧签名再重签，兼容新旧两种数据形态。
     */
    @Override
    public String signUrl(String urlOrKey) {
        String key = FileStorageService.toObjectKey(urlOrKey);
        if (isBlank(key)) {
            return urlOrKey;
        }
        return buildAccessUrl(key, FileStorageService.isPrivatePrefix(prefixOfKey(key)));
    }

    /** 取 key 的第一段作为前缀（如 file/1/a.mp3 → file） */
    private String prefixOfKey(String key) {
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : "";
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
     * 保存服务端生成的字节内容（工具产物等）。
     * <p>
     * 复用 {@link #store(MultipartFile, String)} 的 key 规则与 ACL 策略，
     * 仅数据来源不同：产物由程序/第三方 API 生成，没有 MultipartFile 上下文。
     */
    @Override
    public FileUploadResponse store(byte[] content, String filename, String mimeType, String prefix) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "文件内容为空");
        }
        String name = filename == null ? "output.bin" : filename;
        String ext = FileStorageService.extractExtension(name);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(prefix, fileId, ext.isEmpty() ? ".bin" : ext);
        boolean isPrivate = FileStorageService.isPrivatePrefix(prefix);
        try (InputStream in = new ByteArrayInputStream(content)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            String mime = (mimeType != null && !mimeType.isBlank()) ? mimeType : guessMime(name);
            metadata.setContentType(mime);
            cosClient.putObject(new PutObjectRequest(cosConfig.getBucket(), key, in, metadata));
            // 公开前缀：setObjectAcl 设为 PublicRead，URL 可直接访问
            // 私有前缀：不 setAcl，默认私有读，URL 必须带签名
            if (!isPrivate) {
                try {
                    cosClient.setObjectAcl(cosConfig.getBucket(), key, CannedAccessControlList.PublicRead);
                } catch (Exception aclEx) {
                    // 与 store(MultipartFile) 版本对齐（同一类缺陷，此前只在 MultipartFile 重载上补过补偿）：
                    // put 成功但 setAcl 失败 → 对象保持私有而 URL 是公开直链，访问必然 403；
                    // 修复：删掉这个对象 + 抛错，避免返回"看似可用但实际 403"的 URL，
                    // 同时不留无 ACL、无产物记录的孤儿文件。
                    log.error("setObjectAcl failed after put, deleting orphan key={}", key, aclEx);
                    try {
                        cosClient.deleteObject(cosConfig.getBucket(), key);
                    } catch (Exception delEx) {
                        log.error("Failed to delete orphan after setAcl failure: key={}", key, delEx);
                    }
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "文件上传失败（权限设置异常），请重试");
                }
            }
            String fileUrl = buildAccessUrl(key, isPrivate);
            log.info("Workflow output stored: {} -> {} ({} bytes, private={})", name, key, content.length, isPrivate);
            return new FileUploadResponse(fileId, fileUrl, name, key);
        } catch (IOException e) {
            log.error("Failed to store output to COS: key={}", key, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "产物保存失败，请重试");
        } catch (BusinessException e) {
            // 原样抛出：ACL 补偿分支已在此之前抛出面向用户的文案，
            // 不能被下面的兜底 catch 吞掉并改写成笼统的「产物保存失败」
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error storing output: key={}", key, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "产物保存失败，请重试");
        }
    }

    /** 按扩展名推断 MIME（产物场景没有原始 Content-Type） */
    private String guessMime(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private String buildKey(String prefix, String fileId, String ext) {
        String normalized = FileStorageService.normalizePrefix(prefix);
        return normalized.isEmpty() ? fileId + ext : normalized + "/" + fileId + ext;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
