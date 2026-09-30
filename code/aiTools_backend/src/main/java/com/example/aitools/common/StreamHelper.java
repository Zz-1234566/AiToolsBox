package com.example.aitools.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * SSE 流式响应辅助：统一 emitter 创建、线程调度、chunk 转发、错误兜底。
 * 消除 Controller 端的 5 处重复模板代码（AGENTS.md 第 2 节禁止在 Controller 写业务）。
 */
@Slf4j
public final class StreamHelper {

    private StreamHelper() {}

    /**
     * 启动一个 SSE 流式任务：
     * - 创建 SseEmitter（超时由 {@link Constants#SSE_TIMEOUT_MS} 决定）
     * - 在指定 Executor 中异步执行业务逻辑
     * - 业务返回的每个 chunk 通过 emitter 推送给前端
     * - 业务抛异常 → completeWithError
     * - 业务正常完成 → complete
     *
     * @param executor  线程池（Controller 注入的 ThreadPoolTaskExecutor）
     * @param onChunk 业务逻辑的 chunk 消费函数（lambda 里调 service 的 *Stream 方法）
     * @return 已开始的 SseEmitter
     */
    public static SseEmitter stream(Executor executor, SseChunkTask onChunk) {
        SseEmitter emitter = new SseEmitter(Constants.SSE_TIMEOUT_MS);
        // 超时回调：AI 慢或线程池被占满时，给前端一个明确原因（否则只会看到裸断开）
        emitter.onTimeout(() -> {
            log.warn("SSE stream timeout after {}ms", Constants.SSE_TIMEOUT_MS);
            try {
                sendErrorFrame(emitter, "处理超时，请稍后重试");
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE timeout complete failed", e);
            }
        });
        emitter.onError(t -> log.warn("SSE emitter error: {}", t == null ? "unknown" : t.getMessage()));
        executor.execute(() -> {
            try {
                onChunk.run(emitter);
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try {
                    // 关键：先发一帧用户可读的错误信息，再正常结束。
                    // 若直接 completeWithError，Spring 会异常中断 async request，
                    // 前端拿不到任何原因（HTTP 状态不可预测/空 body），只能显示"网络错误"。
                    sendErrorFrame(emitter, userMessageOf(e));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("SSE error frame send failed, fallback to completeWithError", ex);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignore) {
                        log.warn("SSE completeWithError failed (emitter already closed)", ignore);
                    }
                }
            }
        });
        return emitter;
    }

    /**
     * 发送面向用户的错误帧。
     * <p>约定：{@code --- [ERROR:文案] ---}，与 B2 批量标记同格式（前端按 marker 解析）。
     * <p>注意：文案中不能出现 {@code ]}，否则会破坏前端 marker 正则
     * {@code /^---\s*\[(.+?)\]\s*---$/} 的匹配（非贪婪 + 右括号冲突）。
     */
    private static void sendErrorFrame(SseEmitter emitter, String userMessage) throws IOException {
        String safe = userMessage == null ? "" : userMessage.replace("]", " ");
        emitter.send(SseEmitter.event().data("--- [ERROR:" + safe + "] ---"));
    }

    /**
     * 提取给用户看的错误文案：业务异常用其可读文案，其他异常统一通用文案。
     * <p>原始异常（含堆栈）已由调用处写入日志，不透给前端。
     */
    private static String userMessageOf(Throwable e) {
        if (e instanceof com.example.aitools.exception.BusinessException) {
            return e.getMessage();
        }
        return ResultCode.SYSTEM_ERROR.getMessage();
    }

    /**
     * 把业务 chunk 推送到 emitter
     */
    public static void sendChunk(SseEmitter emitter, String chunk) throws IOException {
        if (chunk == null) return;
        emitter.send(SseEmitter.event().data(chunk));
    }

    /**
     * 业务 lambda 签名：拿到 emitter，把每个 chunk 推出去
     */
    @FunctionalInterface
    public interface SseChunkTask {
        void run(SseEmitter emitter) throws Exception;
    }

    /**
     * 便捷 Consumer 形式：业务只关心 chunk，不直接用 emitter
     * 用于 service *Stream 方法的 Consumer<String> 回调
     */
    public static Consumer<String> asChunkConsumer(SseEmitter emitter) {
        return chunk -> {
            try {
                sendChunk(emitter, chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
