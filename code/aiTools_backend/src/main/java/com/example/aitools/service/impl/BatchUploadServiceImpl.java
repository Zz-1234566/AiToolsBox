package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.BatchUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class BatchUploadServiceImpl implements BatchUploadService {

    @Override
    public List<BatchFilePayload> toPayloads(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Path dir = BatchFilePayload.ensureTempDir();
        List<BatchFilePayload> payloads = new ArrayList<>(files.size());
        try {
            for (MultipartFile f : files) {
                if (f == null) {
                    payloads.add(BatchFilePayload.ofBytes(new byte[0], "未命名"));
                    continue;
                }
                String name = (f.getOriginalFilename() == null || f.getOriginalFilename().isBlank())
                        ? "未命名" : f.getOriginalFilename();
                // 临时文件名用 UUID：原始文件名可能含路径分隔符 / 中文 / 特殊字符，不能直接拼进路径
                Path temp = dir.resolve(UUID.randomUUID().toString());
                // 流式落盘：4KB 缓冲逐段拷贝，堆占用恒定（不调 getBytes 整块进内存）
                BatchFilePayload.copyToFile(f, temp);
                payloads.add(BatchFilePayload.ofTempFile(temp, name, null));
            }
            return payloads;
        } catch (IOException | RuntimeException e) {
            // 中途失败：把已落盘的文件清掉，不留半截临时文件
            cleanup(payloads);
            log.error("[B2] 批量文件落盘失败 files={}", files.size(), e);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(),
                    "读取上传文件失败，请重试");
        }
    }

    @Override
    public void cleanup(List<BatchFilePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        for (BatchFilePayload p : payloads) {
            if (p != null) {
                p.dispose();
            }
        }
    }

    /**
     * 清理某个临时目录下超过指定时长的残留文件（兜底：进程被 kill 时 finally 不会执行）。
     *
     * <p>由定时任务调用，见 {@code BatchTaskRecoveryJob}。
     *
     * @param tempDir 临时目录
     * @param olderThanMs 早于这个时长的文件才删
     * @return 删除的文件数
     */
    @Override
    public int purgeStaleTempFiles(Path tempDir, long olderThanMs) {
        if (tempDir == null || !Files.isDirectory(tempDir)) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - olderThanMs;
        int deleted = 0;
        try (var stream = Files.list(tempDir)) {
            for (Path p : stream.toList()) {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff && Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    log.warn("[B2] 清理残留临时文件失败 path={}", p, e);
                }
            }
        } catch (IOException e) {
            log.warn("[B2] 扫描批量临时目录失败 dir={}", tempDir, e);
        }
        if (deleted > 0) {
            log.info("[B2] 清理残留批量临时文件 {} 个 dir={}", deleted, tempDir);
        }
        return deleted;
    }
}
