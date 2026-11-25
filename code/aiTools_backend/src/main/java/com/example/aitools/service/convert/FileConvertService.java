package com.example.aitools.service.convert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件格式转换：转发到本地 Universal Converter 服务（FastAPI，默认 127.0.0.1:1453）。
 * <p>
 * 为什么走外挂服务：65+ 格式的互转（含 PDF↔Word/PPT、图片互转）用 Java 全部自研成本过高，
 * 该服务纯本地运行、文件不出网。
 * <p>
 * 音视频一律不开放（转换服务依赖 FFmpeg，当前环境未安装）。
 */
@Slf4j
@Service
public class FileConvertService {

    /** 转换服务地址（可用配置覆盖，便于换机器） */
    @Value("${convert.service-url:http://127.0.0.1:1453}")
    private String serviceUrl;

    private final RestTemplate rest = new RestTemplate();

    /** 各组支持的源扩展名 → 可转目标 */
    private static final Map<String, List<String>> TARGETS = new LinkedHashMap<>();
    static {
        TARGETS.put("image", List.of("jpg", "png", "webp", "bmp", "tiff", "gif"));
        TARGETS.put("pdf", List.of("png", "jpg", "docx", "txt", "md", "html", "pptx", "xlsx"));
        TARGETS.put("docx", List.of("pdf", "txt", "html", "md"));
        TARGETS.put("pptx", List.of("pdf", "png", "jpg"));
        TARGETS.put("data", List.of("csv", "xlsx", "json", "xml", "html", "txt"));
    }

    /** 按源扩展名判断所属类别（不在表里的不支持） */
    public static String categoryOf(String ext) {
        String e = ext == null ? "" : ext.toLowerCase().replaceFirst("^\\.", "");
        if (List.of("jpg", "jpeg", "png", "webp", "bmp", "tiff", "tif", "gif").contains(e)) return "image";
        if ("pdf".equals(e)) return "pdf";
        if (List.of("docx", "doc").contains(e)) return "docx";
        if (List.of("pptx", "ppt").contains(e)) return "pptx";
        if (List.of("csv", "xlsx", "xls", "json", "xml", "html", "txt").contains(e)) return "data";
        return null;
    }

    /** 可转目标（已排除音视频） */
    public List<String> targetsFor(String ext) {
        String c = categoryOf(ext);
        return c == null ? List.of() : TARGETS.getOrDefault(c, List.of());
    }

    /**
     * 上传到转换服务，返回其内部文件名（作为 token 使用）。
     */
    public String upload(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
                }
            });
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> resp = rest.postForEntity(serviceUrl + "/api/upload",
                    new HttpEntity<>(body, headers), Map.class);
            Object name = resp.getBody() == null ? null : resp.getBody().get("filename");
            if (name == null) {
                throw new IllegalStateException("转换服务未返回文件名");
            }
            return String.valueOf(name);
        } catch (Exception e) {
            throw new IllegalStateException("转换服务不可用：" + e.getMessage(), e);
        }
    }

    /**
     * 执行转换并取回结果字节。
     *
     * @return {fileName, bytes}
     */
    public Converted convert(String token, String targetFormat) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("file_path", token);
            req.put("target_format", targetFormat.toLowerCase());
            req.put("quality", "high");
            ResponseEntity<Map> resp = rest.postForEntity(serviceUrl + "/api/convert", req, Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                String err = body == null ? "未知错误" : String.valueOf(body.get("error"));
                throw new IllegalStateException(err);
            }
            String filename = String.valueOf(body.get("filename"));
            ResponseEntity<byte[]> file = rest.getForEntity(serviceUrl + "/api/download/" + filename, byte[].class);
            if (file.getBody() == null || file.getBody().length == 0) {
                throw new IllegalStateException("转换结果为空");
            }
            return new Converted(filename, file.getBody());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("转换失败：" + e.getMessage(), e);
        }
    }

    /** 转换结果 */
    public static class Converted {
        private final String fileName;
        private final byte[] bytes;

        public Converted(String fileName, byte[] bytes) {
            this.fileName = fileName;
            this.bytes = bytes;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getBytes() {
            return bytes;
        }
    }
}
