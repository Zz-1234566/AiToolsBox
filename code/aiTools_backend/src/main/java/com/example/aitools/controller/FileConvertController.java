package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.convert.FileConvertService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件格式转换（本地服务转发）。
 * <p>
 * 三步：上传 → 转换 → 下载并转存 COS。音视频不开放。
 */
@Slf4j
@RestController
@RequestMapping("/api/file-convert")
@RequiredArgsConstructor
public class FileConvertController {

    /** 转换结果短期缓存：token -> {文件名, 字节}，10 分钟过期 */
    private static final java.util.Map<String, Object[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final FileConvertService convertService;
    private final FileStorageService fileStorageService;
    private final AuthUtil authUtil;

    /** 该扩展名可转的目标格式 */
    @GetMapping("/targets")
    public Result<Map<String, Object>> targets(@RequestParam(value = "ext", required = false) String ext,
                                               HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("category", FileConvertService.categoryOf(ext));
        out.put("targets", convertService.targetsFor(ext));
        return Result.success(out);
    }

    /** 第一步：把文件交给转换服务，返回 token */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "请选择要转换的文件");
        }
        if (file.getSize() > Constants.MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(),
                    Constants.fileSizeLimitMessage(Constants.MAX_FILE_SIZE));
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = FileStorageService.extractExtension(original).replaceFirst("^\\.", "");
        String category = FileConvertService.categoryOf(ext);
        if (category == null) {
            throw new BusinessException(ResultCode.FILE_UNSUPPORTED.getCode(),
                    "暂不支持该格式（当前支持：常见图片、PDF、Word、PPT、Excel/CSV/JSON）");
        }
        String token = convertService.upload(file);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("name", original);
        out.put("size", file.getSize());
        out.put("ext", ext.toLowerCase());
        out.put("category", category);
        out.put("targets", convertService.targetsFor(ext));
        return Result.success(out);
    }

    /**
     * 下载转换结果：由服务端控制文件名。
     * <p>
     * 直接给 COS 链接时，浏览器只能拿对象名（UUID）当文件名；
     * 这里从内存缓存读出字节并带上 Content-Disposition，用户得到的才是「原名.jpg」。
     */
    @GetMapping("/download")
    public void download(@RequestParam("token") String token,
                         HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        authUtil.getUserIdFromRequest(request);
        Object[] cached = token == null ? null : CACHE.get(token);
        if (cached == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文件已过期，请重新转换");
        }
        String fileName = String.valueOf(cached[0]);
        byte[] bytes = (byte[]) cached[1];
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /** 第二步：转换 + 转存，返回可下载地址 */
    @PostMapping("/do")
    public Result<Map<String, Object>> convert(@RequestBody Map<String, String> body,
                                               HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        String token = body == null ? null : body.get("token");
        String target = body == null ? null : body.get("targetFormat");
        if (token == null || token.isBlank() || target == null || target.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少文件或目标格式");
        }
        // 白名单校验：只允许该源格式声明的目标，避免前端被改后转出奇怪格式
        String srcExt = token.contains(".") ? token.substring(token.lastIndexOf('.') + 1) : "";
        List<String> allowed = convertService.targetsFor(srcExt);
        if (!allowed.contains(target.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不支持转换为 " + target + "（可选：" + String.join(" / ", allowed) + "）");
        }

        FileConvertService.Converted converted = convertService.convert(token, target);
        FileUploadResponse stored = fileStorageService.store(
                converted.getBytes(), converted.getFileName(), null, "file");
        stored.setFileUrl(FileStorageService.resolveFullUrl(stored.getFileUrl(), request));

        // 缓存字节供下载（文件名由服务端控制，避免浏览器用 COS 的 UUID 命名）
        String downloadToken = java.util.UUID.randomUUID().toString().replace("-", "");
        CACHE.put(downloadToken, new Object[]{converted.getFileName(), converted.getBytes(), System.currentTimeMillis()});
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(e -> now - (long) e.getValue()[2] > CACHE_TTL_MS);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", converted.getFileName());
        out.put("fileUrl", stored.getFileUrl());
        out.put("downloadToken", downloadToken);
        out.put("size", converted.getBytes().length);
        log.info("[file-convert] user={} {} -> {} size={}B", userId, token, target, converted.getBytes().length);
        return Result.success(out);
    }
}
