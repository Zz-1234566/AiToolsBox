package com.example.aitools.ai;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.CosConfig;
import com.example.aitools.config.TencentAsrConfig;
import com.example.aitools.exception.BusinessException;
import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.CreateRecTaskRequest;
import com.tencentcloudapi.asr.v20190614.models.CreateRecTaskResponse;
import com.tencentcloudapi.asr.v20190614.models.DescribeTaskStatusRequest;
import com.tencentcloudapi.asr.v20190614.models.DescribeTaskStatusResponse;
import com.tencentcloudapi.asr.v20190614.models.TaskStatus;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Locale;

/**
 * 腾讯云语音识别（ASR）客户端。
 * <p>
 * 使用云 API 3.0 的「录音文件识别」：
 * <ol>
 *   <li>{@code CreateRecTask} 提交任务（{@code SourceType=1} 内联 base64 音频，免 COS 依赖）</li>
 *   <li>{@code DescribeTaskStatus} 轮询结果直到 {@code Status=2}</li>
 * </ol>
 * <p>
 * <b>实测确认的关键点（SDK 3.1.270 / 云 API 2019-06-14）</b>：
 * <ul>
 *   <li>{@code TaskId} 位于 {@code CreateRecTaskResponse.Data.TaskId}，不是顶层</li>
 *   <li>{@code DescribeTaskStatusResponse.Data} 是<b>单个</b> {@link TaskStatus} 对象（非列表）</li>
 *   <li>{@code Status=2} 表示<b>已完成</b>（{@code StatusStr="success"}），不是失败；
 *       失败时 {@code ErrorMsg} 非空且 {@code Result} 为空</li>
 *   <li>{@code CreateRecTaskRequest} <b>没有</b> {@code SampleRate} / {@code ContentType} 字段，
 *       采样率必须由调用方用 ffmpeg 预转码保证</li>
 *   <li>内联 {@code Data} 上限 5242880 字节（base64 口径），原始文件约 3.7MB 以内</li>
 * </ul>
 * 密钥默认复用 COS 同一套（与 {@code OcrServiceImpl} 同样的复用策略）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentAsrClient {

    private final TencentAsrConfig tencentAsrConfig;
    private final CosConfig cosConfig;

    /**
     * 提交录音文件识别任务并同步等待结果。
     *
     * @param audioBytes 音频字节（调用方需保证 ≤ tencent.maxAudioBytes）
     * @param filename   原始文件名（仅用于日志排查）
     * @return 识别文本
     * @throws BusinessException 未启用 / 缺 AppId / 超限 / 识别失败 / 超时
     */
    public String transcribe(byte[] audioBytes, String filename) {
        if (!isEnabled()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "腾讯云语音识别未启用");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "音频内容为空");
        }
        long max = tencentAsrConfig.getMaxAudioBytes() == null
                ? 3900000L : tencentAsrConfig.getMaxAudioBytes();
        if (audioBytes.length > max) {
            // 明确拦截：避免把腾讯云「Data length should in range」英文错误码透给用户
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(),
                    "音频文件过大（当前 " + (audioBytes.length / 1024 / 1024) + "MB，上限 "
                            + (max / 1024 / 1024) + "MB），请压缩或分段后再试");
        }

        try {
            AsrClient client = buildClient();
            long taskId = createTask(client, audioBytes, filename);
            return awaitResult(client, taskId, filename);
        } catch (BusinessException e) {
            throw e;
        } catch (TencentCloudSDKException e) {
            // 脱敏：腾讯云 requestId / 原始报文只进日志
            log.error("[ASR-tencent] 调用失败 fileName={} code={} msg={}",
                    filename, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        } catch (Exception e) {
            log.error("[ASR-tencent] 识别异常 fileName={}", filename, e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        }
    }

    /** 腾讯云通道是否可用（enabled 且 appId 已配置） */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(tencentAsrConfig.getEnabled())
                && tencentAsrConfig.getAppId() != null
                && !tencentAsrConfig.getAppId().isBlank();
    }

    private AsrClient buildClient() {
        // 密钥优先级：TencentAsrConfig 自己的 > CosConfig 复用（与 OcrServiceImpl 一致）
        String secretId = firstNonBlank(tencentAsrConfig.getSecretId(), cosConfig.getSecretId());
        String secretKey = firstNonBlank(tencentAsrConfig.getSecretKey(), cosConfig.getSecretKey());
        String region = firstNonBlank(tencentAsrConfig.getRegion(), cosConfig.getRegion());
        return new AsrClient(new Credential(secretId, secretKey), region);
    }

    private Long createTask(AsrClient client, byte[] audioBytes, String filename)
            throws TencentCloudSDKException {
        CreateRecTaskRequest req = new CreateRecTaskRequest();
        req.setEngineModelType(tencentAsrConfig.getEngineModelType());
        // 注意：本 SDK（3.1.270）中 ChannelNum / ResTextFormat / SourceType / DataLen / TaskId
        // 均为 Long 装箱类型，不能直接传 int 字面量。
        req.setChannelNum(tencentAsrConfig.getChannelNum() == null
                ? 1L : tencentAsrConfig.getChannelNum().longValue());
        req.setResTextFormat(0L);
        // SourceType=1：内联 base64 音频，无需先上传 COS
        req.setSourceType(1L);
        req.setData(Base64.getEncoder().encodeToString(audioBytes));
        req.setDataLen((long) audioBytes.length);
        // 注意：不要设 SampleRate / ContentType —— 该 Request 无这两个字段，设置了会报「未定义参数」

        CreateRecTaskResponse resp = client.CreateRecTask(req);
        if (resp == null || resp.getData() == null || resp.getData().getTaskId() == null) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "语音识别任务提交失败");
        }
        Long taskId = resp.getData().getTaskId();
        log.info("[ASR-tencent] 任务已提交 taskId={} fileName={} bytes={}",
                taskId, filename, audioBytes.length);
        return taskId;
    }

    private String awaitResult(AsrClient client, Long taskId, String filename)
            throws TencentCloudSDKException {
        long interval = tencentAsrConfig.getPollIntervalMs() == null
                ? 3000L : tencentAsrConfig.getPollIntervalMs();
        int maxTimes = tencentAsrConfig.getMaxPollTimes() == null
                ? 100 : tencentAsrConfig.getMaxPollTimes();

        for (int i = 0; i < maxTimes; i++) {
            sleep(interval);
            DescribeTaskStatusRequest q = new DescribeTaskStatusRequest();
            q.setTaskId(taskId);
            // Data 是单个 TaskStatus 对象，不是列表
            TaskStatus data = client.DescribeTaskStatus(q).getData();
            if (data == null) {
                continue;
            }
            // Status=2 + Result 非空 = 成功（实测确认，Status=2 不是失败）
            Long status = data.getStatus();
            if (status != null && status == 2L) {
                String text = data.getResult() == null ? "" : data.getResult().trim();
                if (!text.isEmpty()) {
                    log.info("[ASR-tencent] 识别成功 taskId={} fileName={} len={}",
                            taskId, filename, text.length());
                    return text;
                }
                String errMsg = data.getErrorMsg() == null ? "" : data.getErrorMsg();
                log.warn("[ASR-tencent] 识别失败 taskId={} fileName={} err={} statusStr={}",
                        taskId, filename, errMsg, data.getStatusStr());
                throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(),
                        errMsg.isEmpty() ? "语音识别失败，请稍后重试" : "语音识别失败，请稍后重试");
            }
        }
        log.error("[ASR-tencent] 轮询超时 taskId={} fileName={} maxTimes={}", taskId, filename, maxTimes);
        throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "语音识别超时，请重试");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "语音识别被中断");
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    /** 保留：便于排查时按扩展名判断（当前实现不依赖格式，由 ffmpeg 统一转码） */
    @SuppressWarnings("unused")
    private static String normalizeExt(String filename) {
        return (filename == null) ? "" : filename.toLowerCase(Locale.ROOT);
    }
}
