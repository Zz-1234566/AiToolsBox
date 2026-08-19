package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    /** 单文件大小上限：20MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** 支持的文件类型：图片 + 文档 */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt");

    /** 支持的存储前缀（目录）：头像 / AI 图片 / 去背景 / 通用用户文件区，空串表示根目录 */
    private static final Set<String> ALLOWED_PREFIXES = Set.of("", "avatar", "ai-image", "ai-bg", "file");

    private final FileStorageService fileStorageService;

    private final AuthUtil authUtil;

    @PostMapping("/upload")
    public Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "prefix", required = false, defaultValue = "avatar") String prefix,
                                             HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件大小不能超过20MB");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不支持的文件类型，仅支持 jpg/png/gif/webp/pdf/doc/docx/txt");
        }
        String normalizedPrefix = FileStorageService.normalizePrefix(prefix);
        if (!ALLOWED_PREFIXES.contains(normalizedPrefix)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的上传目录");
        }

        // file 前缀为通用用户文件区：强制登录，并拼 userId 目录（file/{userId}/），其它前缀保持现状
        String actualPrefix = normalizedPrefix;
        if ("file".equals(normalizedPrefix)) {
            Long userId = authUtil.getUserIdFromRequest(request);
            actualPrefix = "file/" + userId;
        }

        FileUploadResponse response = fileStorageService.store(file, actualPrefix);

        // 本地存储返回相对路径（/uploads/xxx），拼装成完整访问地址；COS 返回的已是完整地址
        if (response.getFileUrl().startsWith("/")) {
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                    + (request.getServerPort() == 80 || request.getServerPort() == 443
                    ? "" : ":" + request.getServerPort());
            response.setFileUrl(baseUrl + response.getFileUrl());
        }
        return Result.success("上传成功", response);
    }

    private String extractExtension(String originalFilename) {
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
