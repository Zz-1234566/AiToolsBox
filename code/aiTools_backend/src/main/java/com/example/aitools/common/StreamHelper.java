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
        executor.execute(() -> {
            try {
                onChunk.run(emitter);
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignore) {
                    // emitter 已关闭，忽略
                }
            }
        });
        return emitter;
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
