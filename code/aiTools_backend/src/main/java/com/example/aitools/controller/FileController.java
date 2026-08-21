package com.example.aitools.controller;

import com.example.aitools.common.Constants;
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

/**
 * 通用文件上传 Controller。
 * 只做：参数校验（类型 / 大小 / 目录） + 鉴权（file 前缀需登录） + 调 service。
 * 公共字段 → Constants；扩展名提取 / baseUrl 拼装 / file 路由 → FileStorageService 的 default 方法。
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final AuthUtil authUtil;

    @PostMapping("/upload")
    public Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "prefix", required = false, defaultValue = "avatar") String prefix,
                                             HttpServletRequest request) {
        // 1) 参数校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择要上传的文件");
        }
        if (file.getSize() > Constants.MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件大小不能超过20MB");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = FileStorageService.extractExtension(originalFilename).replaceFirst("^\\.", "");
        if (!Constants.ALLOWED_FILE_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不支持的文件类型，仅支持 jpg/png/gif/webp/pdf/doc/docx/txt");
        }

        // 2) 目录校验 + 用户文件区路由
        String normalizedPrefix = FileStorageService.normalizePrefix(prefix);
        if (!Constants.ALLOWED_FILE_PREFIXES.contains(normalizedPrefix)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的上传目录");
        }
        Long userId = Constants.FILE_PREFIX_USER_FILE.equals(normalizedPrefix)
                ? authUtil.getUserIdFromRequest(request) : null;
        String actualPrefix = FileStorageService.resolveUserFilePrefix(normalizedPrefix, userId);

        // 3) 调 service 存文件，把返回的 pathOrUrl 拼成完整 URL
        FileUploadResponse response = fileStorageService.store(file, actualPrefix);
        response.setFileUrl(FileStorageService.resolveFullUrl(response.getFileUrl(), request));

        return Result.success("上传成功", response);
    }
}
