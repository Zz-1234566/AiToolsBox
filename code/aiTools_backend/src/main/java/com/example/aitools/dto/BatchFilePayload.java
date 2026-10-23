package com.example.aitools.dto;

import com.example.aitools.common.Constants;
import com.example.aitools.common.MimeTypes;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 批量任务文件载体。
 *
 * <p><b>为什么落磁盘而不是全量进内存</b>：
 * 批量上传的总大小上限是 200MB（见 {@link Constants#BATCH_MAX_TOTAL_SIZE}）。
 * 如果像早期实现那样在 Controller 里对每个文件调 {@code MultipartFile.getBytes()}，
 * 再把 10 个文件的 byte[] 一起装进 List，那么**整批的字节会同时驻留在堆里**：
 * <pre>
 *   上传 200MB  →  堆里同时活着 200MB 的 byte[]
 *   3 个用户并发 →  600MB live heap
 * </pre>
 * 默认 JVM 堆是物理内存的 1/4，2GB 机器上只有 512MB —— 一次并发批量就能把堆打满，
 * 表现为 {@code OutOfMemoryError}，而且**受害的是整个后端进程**（不只是这一个请求）。
 *
 * <p>本类的做法：
 * <ol>
 *   <li><b>落盘</b>：Controller 侧把每个上传文件**流式复制**到我们自己的临时文件
 *       （{@code BatchUploadService}），堆占用 ≈ 0；</li>
 *   <li><b>懒加载</b>：{@link #getContent()} 首次调用时才把该文件读进堆，
 *       并缓存在 {@code content} 字段里（同一文件多次调用只读一次盘）；</li>
 *   <li><b>用完即释放</b>：批量循环处理完一个文件后调 {@link #release()} 清掉缓存，
 *       于是**峰值堆 = 单个文件大小（≤20MB）**，与批量总大小无关。</li>
 * </ol>
 *
 * <p>另外，Tomcat 会在请求结束时删掉 multipart 的临时文件，而批量任务是
 * **异步**跑的（HTTP 已返回、异步线程还在处理），所以不能直接把 MultipartFile
 * 交给异步线程 —— 必须先固化。这正是本类存在的理由。
 *
 * <p>⚠️ 用完必须调 {@link #deleteTempFile()}（或用 {@code BatchUploadService.cleanup} 统一删），
 * 否则临时文件会堆积在磁盘上。
 */
@Slf4j
public class BatchFilePayload {

    /**
     * 内容所在的临时文件路径（磁盘模式）。
     * <p>null 表示纯内存模式（{@link #ofBytes} 构造，NodeExecutor 的工作流场景用）。
     */
    private final String tempPath;

    /** 原始文件名（保留扩展名用于后缀判断、显示） */
    private final String originalFilename;

    /**
     * 可选：批量场景下若想所有文件共用一个 BYOK key，由 Controller 一次性传入；
     * 单文件则忽略，留给 service 层走 resolver 走默认。
     */
    private final String apiKey;

    /**
     * 懒加载的字节缓存 —— 只缓存**当前这一个文件**的字节。
     * <p>批量循环里处理完一个文件就要 {@link #release()}，否则 N 个文件的字节
     * 又会同时驻留（等于没改）。
     */
    private transient byte[] content;

    // ==================== 构造 ====================

    private BatchFilePayload(String tempPath, byte[] content, String originalFilename, String apiKey) {
        this.tempPath = tempPath;
        this.content = content;
        this.originalFilename = originalFilename;
        this.apiKey = apiKey;
    }

    /**
     * 磁盘模式：内容在临时文件里（Controller 落盘后使用）。
     */
    public static BatchFilePayload ofTempFile(Path tempFile, String originalFilename, String apiKey) {
        return new BatchFilePayload(tempFile.toAbsolutePath().toString(), null,
                normalizeName(originalFilename), apiKey);
    }

    /**
     * 内存模式：内容已在内存里（工作流场景用，文件本来就在本地 / 已下载到内存）。
     */
    public static BatchFilePayload ofBytes(byte[] content, String originalFilename) {
        return ofBytes(content, originalFilename, null);
    }

    /**
     * 内存模式：内容已在内存里。
     */
    public static BatchFilePayload ofBytes(byte[] content, String originalFilename, String apiKey) {
        return new BatchFilePayload(null, content == null ? new byte[0] : content,
                normalizeName(originalFilename), apiKey);
    }

    private static String normalizeName(String name) {
        return (name == null || name.isBlank()) ? "未命名" : name;
    }

    // ==================== 访问 ====================

    /**
     * 取文件字节。磁盘模式下**首次调用才读盘**并缓存，同一文件多次调用只读一次。
     *
     * <p>不抛 checked 异常：读盘失败属于「这个文件坏了」，按业务异常处理更合适
     * （调用点都在批量循环里，抛 checked 异常会把签名一路改下去）。
     *
     * @return 文件字节；读失败返回空数组（调用点按「文件为空」处理）
     */
    public byte[] getContent() {
        if (content != null) {
            return content;
        }
        if (tempPath == null) {
            return new byte[0];
        }
        try {
            content = Files.readAllBytes(Path.of(tempPath));
            return content;
        } catch (IOException e) {
            log.warn("[B2] 读取批量临时文件失败 path={}", tempPath, e);
            return new byte[0];
        }
    }

    /** 原始文件名 */
    public String getOriginalFilename() {
        return originalFilename;
    }

    /** 批量场景共用的 BYOK key（可空） */
    public String getApiKey() {
        return apiKey;
    }

    /** 临时文件路径（纯内存模式返回 null） */
    public String getTempPath() {
        return tempPath;
    }

    /** 文件字节大小（不触发读盘；磁盘模式读文件长度，内存模式取数组长度） */
    public long size() {
        if (content != null) {
            return content.length;
        }
        if (tempPath == null) {
            return 0L;
        }
        try {
            return Files.size(Path.of(tempPath));
        } catch (IOException e) {
            return 0L;
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 释放已缓存的字节（**不删临时文件**）。
     * <p>批量循环里处理完一个文件就调它 → 峰值堆降到「单个文件大小」。
     */
    public void release() {
        content = null;
    }

    /**
     * 删除临时文件（磁盘模式）。
     * <p>内存模式是空操作。删除失败只 warn —— 临时文件残留顶多是占磁盘，
     * 不该因此让整个批量任务失败。
     */
    public void deleteTempFile() {
        if (tempPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(tempPath));
        } catch (IOException e) {
            log.warn("[B2] 删除批量临时文件失败 path={}", tempPath, e);
        }
    }

    /**
     * 释放字节并删除临时文件（一个文件处理完的完整收尾）。
     */
    public void dispose() {
        release();
        deleteTempFile();
    }

    /**
     * 把自己包成内存版 MultipartFile（基于 ByteArrayInputStream），供 DocumentParser 等仍接收 MultipartFile 的 API 使用
     * 避免引入 spring-test（test scope）里的 MockMultipartFile
     */
    public MultipartFile toMultipartFile() {
        final byte[] bytes = getContent();
        final String name = originalFilename;
        return new MultipartFile() {
            @Override public String getName() { return name; }
            @Override public String getOriginalFilename() { return name; }
            @Override public String getContentType() { return MimeTypes.OCTET_STREAM; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() throws IOException { return bytes; }
            @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(File dest) throws IOException, IllegalStateException {
                throw new UnsupportedOperationException("InMemoryMultipartFile 不支持 transferTo");
            }
        };
    }

    /**
     * 把一个 MultipartFile 的内容**流式**写到目标文件（不经过堆）。
     *
     * <p>用 4KB 缓冲逐段拷贝，堆占用恒定 —— 这是「上传不撑爆堆」的关键：
     * 直接调 {@code MultipartFile.getBytes()} 会一次性分配整个文件大小的数组。
     *
     * @param file   上传的文件
     * @param target 目标文件
     * @throws IOException 读写失败
     */
    public static void copyToFile(MultipartFile file, Path target) throws IOException {
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 校验并创建批量上传的临时目录。
     *
     * @return 临时目录路径
     * @throws BusinessException 目录不可创建
     */
    public static Path ensureTempDir() {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), Constants.BATCH_TEMP_DIR_NAME);
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            log.error("[B2] 创建批量临时目录失败 dir={}", dir, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(),
                    "服务器临时目录不可用，请稍后重试");
        }
    }
}
