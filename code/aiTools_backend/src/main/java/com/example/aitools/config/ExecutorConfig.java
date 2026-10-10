package com.example.aitools.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置：P0-A1 改造，把原来的全局单线程 Executor 拆成两个池。
 * <ul>
 *   <li>{@code streamExecutor}：SSE 流式任务（用户请求 → AI 逐块推送），核心 8 最大 32</li>
 *   <li>{@code batchExecutor}：批量任务（多文件上传 + 异步处理），核心 4 最大 16</li>
 * </ul>
 * 拆分原因：批量任务跑 10 文件 AI 整理时，10 秒之内不应该阻塞其他用户的流式总结。
 * <p>
 * <b>拒绝策略为 {@link ThreadPoolExecutor.AbortPolicy}，不是 CallerRunsPolicy</b>：
 * 这两个池的任务都由 HTTP 请求线程直接 {@code execute()} 提交（见 StreamHelper / 各 Controller），
 * 而任务体是阻塞的 AI 调用（SSE 最长可达 10 分钟）。CallerRunsPolicy 的语义是
 * 「不抛异常，由**调用线程**执行该任务」——池满时 300 秒级的任务会直接落在 Tomcat 请求线程上，
 * 把请求线程一起卡死，且此时反压完全失效（上游线程自己也被阻塞住了）。
 * AbortPolicy 抛 {@link java.util.concurrent.RejectedExecutionException}，
 * 由调用方当场转成「明确的错误提示」，请求线程立即释放。
 */
@Slf4j
@Configuration
@EnableAsync
public class ExecutorConfig {

    /** 流式任务线程池 bean 名（Controller @Qualifier 引用） */
    public static final String STREAM_EXECUTOR = "streamExecutor";

    /** 批量任务线程池 bean 名 */
    public static final String BATCH_EXECUTOR = "batchExecutor";

    @Bean(name = STREAM_EXECUTOR)
    public Executor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Spring 容器关闭时等待已在跑的任务完成（最长 30 秒）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("SSE stream executor initialized: core=8, max=32, queue=100");
        return executor;
    }

    @Bean(name = BATCH_EXECUTOR)
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("batch-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Batch task executor initialized: core=4, max=16, queue=50");
        return executor;
    }
}
