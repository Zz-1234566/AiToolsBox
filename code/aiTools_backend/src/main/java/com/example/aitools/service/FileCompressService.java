package com.example.aitools.service;

import com.example.aitools.dto.CompressResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件压缩服务：把超过第三方接口字节限制的文件压到限额以内。
 * <p>
 * <b>与 {@code TranscribeService#transcodeToMp3} 的边界</b>（勿混淆）：
 * <ul>
 *   <li>转码（transcode）：<b>格式归一化</b>，无条件执行，保证 AI 能识别；</li>
 *   <li>压缩（compress）：<b>大小控制</b>，仅在超限时执行，保证不撞接口字节限制。</li>
 * </ul>
 * 两者不合并。
 * <p>
 * <b>失败语义</b>：迭代用尽仍超限 → 抛 {@code BusinessException}（错误码 FILE_TOO_LARGE），
 * <b>不</b>回退原文件。理由：调用方拿到原文件后必然在第三方接口处再次失败，
 * 届时用户只会看到「未知错误」；宁可在此处明确告知「文件过大」。
 * <p>
 * <b>不做的事</b>：PDF / Word / txt 不做压缩 —— 项目内走
 * {@code DocumentParser} 本地抽文字层后截断，不经第三方字节限制；
 * 且 PDF、docx 内部流已压缩，重写压不动。
 * <p>
 * <b>迭代写法</b>：参考 GitHub 成熟实现（Stirling-PDF 93k★ / Tiny / EasyImageCompressor），
 * 采用「while + 单调递减 + 明确下界」而非递归，结构上不可能死循环。
 */
public interface FileCompressService {

    /**
     * 把文件压到 {@code maxBytes} 以内。
     *
     * @param file     上传文件
     * @param maxBytes 目标字节数（原始文件已 ≤ 此值时原样返回，不做任何处理）
     * @return 压缩结果（content 恒为非空 byte[]）
     * @throws com.example.aitools.exception.BusinessException 压缩未启用 / 类型不支持 /
     *                                                   ffmpeg 不可用 / 迭代用尽仍超限
     */
    CompressResult compressTo(MultipartFile file, long maxBytes);

    /**
     * 字节流重载：供批量任务使用（文件已在内存，避开 MultipartFile 临时文件被清理）。
     *
     * @param bytes    文件字节
     * @param filename 原始文件名（用于判定类别与日志）
     * @param maxBytes 目标字节数
     * @return 压缩结果
     */
    CompressResult compressBytes(byte[] bytes, String filename, long maxBytes);
}
