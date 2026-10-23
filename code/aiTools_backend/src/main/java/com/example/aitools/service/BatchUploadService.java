package com.example.aitools.service;

import com.example.aitools.dto.BatchFilePayload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 批量上传文件的**落盘与清理**服务。
 *
 * <p><b>为什么要有这一层</b>：批量上传的总大小上限是 200MB。
 * 如果在 Controller 里对每个文件调 {@code MultipartFile.getBytes()}，
 * 整批字节会同时驻留在堆里（详见 {@link BatchFilePayload} 的类注释）——
 * 并发几个批量请求就能把堆打满，拖垮整个后端。
 *
 * <p>本服务把两件事集中到一处：
 * <ol>
 *   <li>{@link #toPayloads}：把上传文件**流式落盘**到临时目录，堆占用 ≈ 0；</li>
 *   <li>{@link #cleanup}：异步任务结束后删掉临时文件（必须在 finally 里调）。</li>
 * </ol>
 *
 * <p>调用范式（Controller）：
 * <pre>{@code
 * List<BatchFilePayload> payloads = batchUploadService.toPayloads(files); // 失败时已自行清理
 * batchExecutor.execute(() -> {
 *     try {
 *         aiOfficeToolService.xxxBatchStream(userId, payloads, ...);
 *     } finally {
 *         batchUploadService.cleanup(payloads);   // 必须：否则临时文件堆积
 *     }
 * });
 * }</pre>
 */
public interface BatchUploadService {

    /**
     * 把上传的文件流式落盘，返回可在异步线程中安全使用的载体。
     *
     * <p>全程不把文件内容读进堆：每个文件用 4KB 缓冲逐段写到临时文件。
     *
     * <p><b>失败语义</b>：中途某个文件写盘失败时，
     * 已落盘的文件会被清理干净，然后抛出异常（不留下半截临时文件）。
     *
     * @param files 上传的文件列表
     * @return 与入参等长的载体列表
     * @throws com.example.aitools.exception.BusinessException 读文件 / 写盘失败
     */
    List<BatchFilePayload> toPayloads(List<MultipartFile> files);

    /**
     * 清理临时文件（释放字节缓存 + 删磁盘文件）。
     *
     * <p><b>必须在异步任务的 finally 里调用</b>，否则临时文件会一直堆在磁盘上。
     * 传 null / 空列表是安全的（空操作）。
     *
     * @param payloads 载体列表
     */
    void cleanup(List<BatchFilePayload> payloads);

    /**
     * 清理临时目录里「放太久没人管」的残留文件（兜底）。
     *
     * <p>正常路径下临时文件在异步任务结束时就删了；这里处理的是
     * 「进程被 kill，finally 没来得及执行」的情况 —— 文件会留在磁盘上堆积。
     *
     * @param tempDir     临时目录
     * @param olderThanMs 早于这个时长的文件才删
     * @return 删除的文件数
     */
    int purgeStaleTempFiles(java.nio.file.Path tempDir, long olderThanMs);
}
