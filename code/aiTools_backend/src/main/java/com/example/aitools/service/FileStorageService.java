package com.example.aitools.service;

import com.example.aitools.common.Constants;
import com.example.aitools.dto.FileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文件存储服务抽象。
 * 默认实现 {@link com.example.aitools.service.impl.LocalFileStorageService} 存本地，
 * 配置 cos.enabled=true 时切换为 {@link com.example.aitools.service.impl.CosFileStorageService}（腾讯云 COS）。
 * 存储按前缀（目录）区分用途：avatar/ 头像、ai-image/ AI 图片、ai-bg/ 去背景等。
 */
public interface FileStorageService {

    /**
     * 保存上传的文件
     *
     * @param file   上传的文件（类型与大小已由 Controller 校验）
     * @param prefix 存储前缀目录，如 avatar、ai-image、ai-bg；空串表示根目录
     * @return 文件ID、访问URL、原始文件名
     */
    FileUploadResponse store(MultipartFile file, String prefix);

    /**
     * 保存服务端生成的字节内容（工具产物、抠图结果等，非用户上传的场景）。
     * <p>
     * 与 {@link #store(MultipartFile, String)} 的区别：产物是程序生成或第三方 API 返回的，
     * 没有 MultipartFile 上下文；本方法补齐文件名、MIME 与大小后走同一套存储逻辑。
     *
     * @param content   文件字节
     * @param filename  文件名（含扩展名，用于推断 MIME）
     * @param mimeType  MIME 类型；为空时按扩展名推断
     * @param prefix    存储前缀目录，如 tool-output（工具产物）、ai-bg（去背景）
     * @return 文件ID、访问URL、原始文件名
     */
    FileUploadResponse store(byte[] content, String filename, String mimeType, String prefix);

    /**
     * 按对象 key 实时生成一个可访问 URL（查询历史记录等「读出即签发」的场景）。
     * <p>
     * 入参允许是完整 URL 或纯 key，内部统一经 {@link #toObjectKey} 还原，
     * 因此<b>库内存的老数据（带 ?sign= 的过期 URL）也能直接传入</b>：
     * 剥掉旧签名 → 按当前有效期重新签发，返回一定可用的新链接。
     * <ul>
     *   <li>私有前缀（file/）→ 新签名 URL，有效期见 cos.signed-url-ttl-seconds</li>
     *   <li>公开前缀 / 本地存储 → 直链，原样返回</li>
     * </ul>
     */
    String signUrl(String urlOrKey);

    /**
     * 归一化前缀：去除首尾空白与斜杠，空串表示根目录
     */
    static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String p = prefix.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 提取文件扩展名（含点，如 ".jpg"），统一小写。找不到点返回空串。
     * default 方法：3 个调用方共用，避免在 Controller / Local / Cos 各自重复。
     *
     * <p><b>调用约定（勿改）</b>：返回值<b>自带前导点</b>，拼接文件名时直接相加即可，
     * <b>不要再手动加 "."</b>；只有需要「不带点」的比较场景（如扩展名白名单校验）才用
     * {@code replaceFirst("^\\.", "")} 剥掉。反例：{@code "." + extractExtension("a.png")}
     * 会得到 {@code "..png"}，生成 {@code uuid..png} 这类双扩展名 key。
     */
    static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 把 COS 访问地址还原成对象 key（剥掉域名与签名 query）。
     * <p>
     * 用于「库内存 key、查询时实时签发」的链路：
     * <pre>
     * https://b.cos.ap-guangzhou.myqcloud.com/file/1/a.mp3?sign=xxx
     *   → file/1/a.mp3
     * </pre>
     * <p><b>入参两种形态都要兼容</b>（老数据带签名、新数据已是纯 key）：
     * <ul>
     *   <li>纯 key（无 scheme、无 {@code ?}）→ 原样返回，不抛异常</li>
     *   <li>完整 URL → 去域名、截断 {@code ?} 后的 query、去首斜杠、URL 解码</li>
     * </ul>
     * 签名参数全部位于 query，截断 {@code ?} 即完成剥签名；不做 URL 解码会导致
     * key 中含 {@code %} 的对象（如 {@code a%20b.mp3}）取回时 404。
     */
    static String toObjectKey(String urlOrKey) {
        if (urlOrKey == null) return null;
        String s = urlOrKey.trim();
        if (s.isEmpty()) return s;
        // 截断 query：签名参数全在 ? 之后
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        // 去掉协议与域名：https://bucket.cos.region.myqcloud.com/xxx
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            int slash = s.indexOf('/', scheme + 3);
            s = slash >= 0 ? s.substring(slash + 1) : "";
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        // key 段做过 URL 编码，解回真实字符
        if (s.indexOf('%') >= 0) {
            try {
                s = URLDecoder.decode(s, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // 非法的百分号转义（如文件名里本来就有 %）→ 保留原样
            }
        }
        return s;
    }

    /**
     * 把 service 返回的 pathOrUrl 转成前端可用的完整 URL。
     * - 已包含 scheme(http/https) → 原样返回（如 COS 已返回完整地址）
     * - 否则按 request 拼 baseUrl（本地存储返回的 /uploads/xxx 走这里）
     */
    static String resolveFullUrl(String pathOrUrl, HttpServletRequest request) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return pathOrUrl;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        if (request == null) return pathOrUrl; // 兜底
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                    ? "" : ":" + request.getServerPort());
        return baseUrl + pathOrUrl;
    }

    /**
     * 通用用户文件区路由：file/{userId}/ 强制登录后拼 userId 子目录。
     * 其他前缀保持不变。
     */
    static String resolveUserFilePrefix(String normalizedPrefix, Long userId) {
        if (Constants.FILE_PREFIX_USER_FILE.equals(normalizedPrefix)) {
            return Constants.FILE_PREFIX_USER_FILE + "/" + userId;
        }
        return normalizedPrefix;
    }

    /**
     * 是否私有前缀（仅 file/ 用户文件区）。
     * 私有前缀 → 走临时签名 URL，5 分钟有效；前端不能缓存。
     * 其他前缀（avatar/ai-image/ai-bg）公开读，返回直链。
     */
    static boolean isPrivatePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return false;
        // 处理过的 prefix 形如 "file/123"（带 userId 子目录）
        return prefix.startsWith(Constants.FILE_PREFIX_USER_FILE + "/") || prefix.equals(Constants.FILE_PREFIX_USER_FILE);
    }

    /**
     * 是否公开前缀（默认 true，保持原有行为）
     */
    static boolean isPublicPrefix(String prefix) {
        return !isPrivatePrefix(prefix);
    }
}
