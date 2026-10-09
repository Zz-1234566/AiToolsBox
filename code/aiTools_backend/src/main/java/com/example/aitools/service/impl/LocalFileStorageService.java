package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.FileConfig;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件存储实现（默认）。
 * 文件保存到 {@code file.upload-dir} 配置的目录（默认 uploads/），
 * 通过静态资源映射 /uploads/** 对外提供访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cos.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final FileConfig fileConfig;

    private Path uploadDir;

    @PostConstruct
    public void init() throws IOException {
        uploadDir = Paths.get(fileConfig.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        log.info("Local file storage initialized at {}", uploadDir);
    }

    @Override
    public FileUploadResponse store(MultipartFile file, String prefix) {
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = FileStorageService.extractExtension(originalFilename);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storedName = fileId + ext;
        String prefixPath = FileStorageService.normalizePrefix(prefix);
        Path dir = prefixPath.isEmpty() ? uploadDir : uploadDir.resolve(prefixPath).normalize();
        // P2-S1（清单漏改，补回）: 深度防御 — 校验最终路径仍在 uploadDir 内，
        // 阻止 prefix 含 "../" 跳出 uploads 目录（即便白名单被未来放宽也不会裸奔）
        if (!dir.startsWith(uploadDir)) {
            log.warn("Resolved path escapes uploadDir: prefix={}, dir={}", prefix, dir);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "非法的存储路径");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storedName));
        } catch (IOException e) {
            log.error("Failed to store file locally: {}", storedName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "文件保存失败，请重试");
        }
        log.info("File stored locally: {} -> {}", originalFilename, storedName);
        // 返回相对路径，由 Controller 拼装完整访问地址
        String urlPath = prefixPath.isEmpty() ? "/uploads/" + storedName : "/uploads/" + prefixPath + "/" + storedName;
        return new FileUploadResponse(fileId, urlPath, originalFilename, null);
    }

    /**
     * 保存服务端生成的字节内容（工作流产物等）。
     * <p>
     * 与 {@link #store(MultipartFile, String)} 共用同一套路径规则与防目录逃逸校验，
     * 仅写入方式不同（无 MultipartFile.transferTo 可用，直接写字节）。
     */
    @Override
    public FileUploadResponse store(byte[] content, String filename, String mimeType, String prefix) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "文件内容为空");
        }
        String name = filename == null ? "output.bin" : filename;
        String ext = FileStorageService.extractExtension(name);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storedName = fileId + (ext.isEmpty() ? ".bin" : "." + ext);
        String prefixPath = FileStorageService.normalizePrefix(prefix);
        Path dir = prefixPath.isEmpty() ? uploadDir : uploadDir.resolve(prefixPath).normalize();
        if (!dir.startsWith(uploadDir)) {
            log.warn("Resolved path escapes uploadDir: prefix={}, dir={}", prefix, dir);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "非法的存储路径");
        }
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(storedName), content);
        } catch (IOException e) {
            log.error("Failed to store output locally: {}", storedName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "产物保存失败，请重试");
        }
        log.info("Workflow output stored locally: {} -> {} ({} bytes)", name, storedName, content.length);
        String urlPath = prefixPath.isEmpty()
                ? "/uploads/" + storedName : "/uploads/" + prefixPath + "/" + storedName;
        return new FileUploadResponse(fileId, urlPath, name, null);
    }
}
