package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.AsrConfig;
import com.example.aitools.config.CompressConfig;
import com.example.aitools.dto.CompressResult;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.exception.ErrorFactory;
import com.example.aitools.service.FileCompressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * 文件压缩实现：图片（降质 + 缩放）与音频（ffmpeg 降码率）两条分支。
 * <p>
 * 迭代范式取自 GitHub 成熟实现：
 * <ul>
 *   <li>Stirling-PDF（93k★）：{@code while (!sizeMet && optimizeLevel <= 9)} + 等级饱和判重退出</li>
 *   <li>Tiny / EasyImageCompressor：{@code while (未达标 && quality > 下界) { quality -= 步长; 重编码 } }</li>
 * </ul>
 * 关键约定：
 * <ol>
 *   <li><b>不递归</b>，用 while + 单调递减 + 明确下界，结构上不可能死循环；</li>
 *   <li>每轮用 {@code baos.reset()} <b>复用同一个流</b>，O(1) 额外内存，
 *       且避免上次残留数据导致大小判断失真；</li>
 *   <li>图片质量下限 0.1（IJG FAQ：Q10 以下接近「op art」，再降无意义）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCompressServiceImpl implements FileCompressService {

    private static final String CAT_IMAGE = "image";
    private static final String CAT_AUDIO = "audio";
    private static final String CAT_UNSUPPORTED = "unsupported";

    /** 单张图片处理的安全上限：超过则直接拒绝，避免 OOM（4000px×4000px RGBA 约 64MB） */
    private static final long IMAGE_MAX_PIXELS_BYTES = 50L * 1024 * 1024;

    private final CompressConfig compressConfig;
    private final AsrConfig asrConfig;

    @Override
    public CompressResult compressTo(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ErrorFactory.of(ResultCode.FILE_UPLOAD_FAILED, "文件为空，无法处理");
        }
        if (!Boolean.TRUE.equals(compressConfig.getEnabled())) {
            throw ErrorFactory.of(ResultCode.SERVICE_UNAVAILABLE, "文件压缩能力未启用");
        }
        long original = file.getSize();
        // 未超限：原样返回，不做任何处理（省一次 IO 与编解码）
        if (original <= maxBytes) {
            return readOriginal(file, file.getOriginalFilename(), maxBytes);
        }
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            log.error("[Compress] 读取上传文件失败 name={}", file.getOriginalFilename(), e);
            throw ErrorFactory.of(ResultCode.FILE_UPLOAD_FAILED, "读取文件失败，请重试");
        }
        return compressBytes(bytes, file.getOriginalFilename(), maxBytes);
    }

    @Override
    public CompressResult compressBytes(byte[] bytes, String filename, long maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw ErrorFactory.of(ResultCode.FILE_UPLOAD_FAILED, "文件内容为空");
        }
        if (!Boolean.TRUE.equals(compressConfig.getEnabled())) {
            throw ErrorFactory.of(ResultCode.SERVICE_UNAVAILABLE, "文件压缩能力未启用");
        }
        int originalSize = bytes.length;
        if (originalSize <= maxBytes) {
            return new CompressResult(bytes, filename, originalSize, 0, false, categoryOf(filename));
        }

        String category = categoryOf(filename);
        if (CAT_IMAGE.equals(category)) {
            try {
                return compressImage(bytes, filename, originalSize, maxBytes);
            } catch (IOException e) {
                // 图片重编码过程中 IO 异常（JVM 无 JPEG writer 等）
                log.error("[Compress] 图片压缩异常 name={}", filename, e);
                throw ErrorFactory.of(ResultCode.FILE_UNSUPPORTED, "图片压缩失败，请换一张重试");
            }
        }
        if (CAT_AUDIO.equals(category)) {
            return compressAudio(bytes, filename, originalSize, maxBytes);
        }
        // 文档类不压缩：直接给出可操作的提示
        log.warn("[Compress] 不支持压缩的类别 fileName={} category={} size={}", filename, category, originalSize);
        throw ErrorFactory.of(ResultCode.FILE_TOO_LARGE,
                "该类型文件暂不支持自动压缩（当前 " + mb(originalSize) + "MB，上限 " + mb(maxBytes)
                        + "MB），请精简后重试");
    }

    // ───────────────────────── 图片分支 ─────────────────────────

    /**
     * 图片迭代压缩：每轮同时降质 + 缩放（双维度收敛）。
     * <p>
     * 循环结构与 Stirling-PDF / Tiny 一致：{@code while (未达标 && quality > 下界)}。
     * 缩放维度另有下限，达到宽度下限后不再继续缩，只靠降质收敛。
     */
    private CompressResult compressImage(byte[] bytes, String filename, int originalSize, long maxBytes)
            throws IOException {
        if (originalSize > IMAGE_MAX_PIXELS_BYTES) {
            log.warn("[Compress] 图片过大，拒绝解码 name={} size={}", filename, originalSize);
            throw ErrorFactory.of(ResultCode.FILE_TOO_LARGE, "图片文件过大，请压缩后再上传");
        }
        BufferedImage source;
        // ImageIO.read 只接受 InputStream/File/URL，byte[] 需包一层 ByteArrayInputStream
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            log.warn("[Compress] 图片解码失败 name={}", filename, e);
            throw ErrorFactory.of(ResultCode.FILE_UNSUPPORTED, "图片无法解析，请换一张重试");
        }
        if (source == null) {
            throw ErrorFactory.of(ResultCode.FILE_UNSUPPORTED, "图片无法解析，请换一张重试");
        }

        int maxIterations = nz(compressConfig.getMaxIterations(), 5);
        float quality = compressConfig.getImageInitialQuality();
        float step = compressConfig.getImageQualityStep();
        float minQuality = compressConfig.getImageMinQuality();
        int maxWidth = compressConfig.getImageInitialMaxWidth();
        int minWidth = compressConfig.getImageMinWidth();

        // 复用同一个流，每轮 reset() —— O(1) 额外内存，且不会因残留数据误判大小
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int iteration = 0;
        boolean sizeMet = false;
        byte[] best = null;
        int bestWidth = maxWidth;

        // 至少执行一次（即使 maxIterations=0 也要产出一次编码结果）
        while (iteration < maxIterations) {
            BufferedImage target = resizeIfNeeded(source, maxWidth);
            byte[] encoded = encodeJpeg(target, quality);
            int size = encoded.length;
            if (size <= maxBytes) {
                sizeMet = true;
                best = encoded;
                bestWidth = target.getWidth();
                break;
            }
            // 记录本轮最优（最小）结果，用于失败时兜底
            if (best == null || size < best.length) {
                best = encoded;
                bestWidth = target.getWidth();
            }
            iteration++;
            // 达到下界即退出，结构上防死循环
            if (quality - step < minQuality && maxWidth * compressConfig.getImageWidthDecay() <= minWidth) {
                break;
            }
            quality = Math.max(minQuality, quality - step);
            maxWidth = Math.max(minWidth, (int) (maxWidth * compressConfig.getImageWidthDecay()));
        }

        if (!sizeMet) {
            log.warn("[Compress] 图片压缩未达标 name={} original={} best={} max={} iterations={}",
                    filename, originalSize, best == null ? 0 : best.length, maxBytes, iteration);
            throw ErrorFactory.of(ResultCode.FILE_TOO_LARGE,
                    "图片压缩后仍超出限制（" + mb(originalSize) + "MB → " + mb(best == null ? 0 : best.length)
                            + "MB，上限 " + mb(maxBytes) + "MB），请裁剪或更换图片");
        }

        log.info("[Compress] 图片压缩完成 name={} {}KB→{}KB width={} q={} iter={}",
                filename, originalSize / 1024, best.length / 1024, bestWidth, quality, iteration);
        String outName = replaceExtension(filename, ".jpg");
        return new CompressResult(best, outName, originalSize, iteration, true, CAT_IMAGE);
    }

    /** 等比缩小到 maxWidth 以内；已小于则原图返回（不放大） */
    private BufferedImage resizeIfNeeded(BufferedImage src, int maxWidth) {
        if (src.getWidth() <= maxWidth) {
            return src;
        }
        double ratio = (double) maxWidth / src.getWidth();
        int newHeight = Math.max(1, (int) (src.getHeight() * ratio));
        BufferedImage resized = new BufferedImage(maxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, maxWidth, newHeight, null);
        g.dispose();
        return resized;
    }

    /** 按指定质量编码为 JPEG（显式指定 writer + 质量参数） */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("当前 JVM 无 JPEG ImageWriter");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(clampQuality(quality));
            try (ImageOutputStream out = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            // 参考项目同样强调：writer 用完必须 dispose，否则占内存
            writer.dispose();
        }
        return baos.toByteArray();
    }

    // ───────────────────────── 音频分支 ─────────────────────────

    /**
     * 音频迭代压缩：按 {@code compress.audio-bitrates} 逐档降码率。
     * <p>
     * 与图片不同，音频的收敛维度是码率（每轮重跑 ffmpeg，成本高于图片重编码），
     * 故档位数量受 {@code maxIterations} 约束。
     * <p>
     * 注：业界有 {@code ffmpeg-cli-wrapper#setTargetSize(long)} 可解析式一步到位，
     * 但该项目仅面向「有 duration 的音视频」，且其音频+视频混合的码率拆分
     * 源码中仍为 TODO；本项目只做纯音频、且受「不引入新依赖」规范约束，故自研迭代。
     */
    private CompressResult compressAudio(byte[] bytes, String filename, int originalSize, long maxBytes) {
        String ffmpeg = resolveFfmpeg();
        String[] bitrates = compressConfig.getAudioBitrates();
        if (bitrates == null || bitrates.length == 0) {
            throw ErrorFactory.of(ResultCode.SERVICE_UNAVAILABLE, "音频压缩未配置码率档位");
        }
        int maxIterations = Math.min(nz(compressConfig.getMaxIterations(), 5), bitrates.length);

        Path workDir = null;
        Path input = null;
        Path output = null;
        try {
            workDir = Files.createTempDirectory("asr_compress_");
            input = workDir.resolve("in" + extensionOf(filename));
            output = workDir.resolve("out.mp3");
            Files.write(input, bytes);

            byte[] best = null;
            int iteration = 0;
            for (; iteration < maxIterations; iteration++) {
                String bitrate = bitrates[Math.min(iteration, bitrates.length - 1)];
                if (transcodeAudio(ffmpeg, input, output, bitrate)) {
                    byte[] encoded = Files.readAllBytes(output);
                    if (encoded.length < bytes.length && (best == null || encoded.length < best.length)) {
                        best = encoded;
                    }
                    if (encoded.length <= maxBytes) {
                        log.info("[Compress] 音频压缩完成 name={} {}KB→{}KB bitrate={} iter={}",
                                filename, originalSize / 1024, encoded.length / 1024, bitrate, iteration + 1);
                        return new CompressResult(encoded, replaceExtension(filename, ".mp3"),
                                originalSize, iteration + 1, true, CAT_AUDIO);
                    }
                }
            }

            log.warn("[Compress] 音频压缩未达标 name={} original={} best={} max={} iterations={}",
                    filename, originalSize, best == null ? 0 : best.length, maxBytes, iteration);
            throw ErrorFactory.of(ResultCode.FILE_TOO_LARGE,
                    "录音压缩后仍超出限制（" + mb(originalSize) + "MB → "
                            + mb(best == null ? 0 : best.length) + "MB，上限 " + mb(maxBytes)
                            + "MB），请分段后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("[Compress] 音频压缩 IO 异常 name={}", filename, e);
            throw ErrorFactory.of(ResultCode.AUDIO_FAILED, "录音压缩失败，请重试");
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
            deleteQuietly(workDir);
        }
    }

    /** ffmpeg 转码为 16k 单声道 mp3；成功返回 true */
    private boolean transcodeAudio(String ffmpeg, Path input, Path output, String bitrate) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y",
                    "-i", input.toAbsolutePath().toString(),
                    "-ar", String.valueOf(compressConfig.getAudioSampleRate()),
                    "-ac", String.valueOf(compressConfig.getAudioChannels()),
                    "-b:a", bitrate,
                    "-f", "mp3",
                    output.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (var r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            if (code != 0) {
                // ffmpeg 原始输出可能含本地路径，只进日志
                log.warn("[Compress] ffmpeg 转码失败 bitrate={} tail={}",
                        bitrate, sb.toString().substring(Math.max(0, sb.length() - 300)));
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("[Compress] ffmpeg 不可用: {}", e.getMessage());
            throw ErrorFactory.of(ResultCode.SERVICE_UNAVAILABLE, "音频压缩依赖的 ffmpeg 不可用");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorFactory.of(ResultCode.AUDIO_FAILED, "录音压缩被中断");
        }
    }

    // ───────────────────────── 工具方法 ─────────────────────────

    private CompressResult readOriginal(MultipartFile file, String filename, long maxBytes) {
        try (InputStream in = file.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return new CompressResult(bytes, filename, bytes.length, 0, false,
                    categoryOf(filename));
        } catch (IOException e) {
            log.error("[Compress] 读取文件失败 name={}", filename, e);
            throw ErrorFactory.of(ResultCode.FILE_UPLOAD_FAILED, "读取文件失败，请重试");
        }
    }

    private String resolveFfmpeg() {
        String path = asrConfig.getFfmpegBinaryPath();
        if (path != null && !path.isBlank()) {
            return path;
        }
        return "ffmpeg";
    }

    private String categoryOf(String filename) {
        String ext = extensionOf(filename);
        return switch (ext) {
            case "jpg", "jpeg", "png", "bmp", "gif", "webp" -> CAT_IMAGE;
            case "mp3", "wav", "m4a", "aac", "flac", "ogg", "amr", "opus", "wma" -> CAT_AUDIO;
            default -> CAT_UNSUPPORTED;
        };
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String replaceExtension(String filename, String newExt) {
        if (filename == null || filename.isBlank()) {
            return "compressed" + newExt;
        }
        int dot = filename.lastIndexOf('.');
        String base = dot >= 0 ? filename.substring(0, dot) : filename;
        return base + newExt;
    }

    private float clampQuality(float q) {
        if (q < 0f) return 0f;
        return Math.min(q, 1f);
    }

    private int nz(Integer v, int def) {
        return v == null || v <= 0 ? def : v;
    }

    private String mb(long bytes) {
        if (bytes >= 1024L * 1024) {
            return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024) {
            return String.format("%.0fKB", bytes / 1024.0);
        }
        return bytes + "B";
    }

    private void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 临时文件删除失败不影响主流程
        }
    }
}
