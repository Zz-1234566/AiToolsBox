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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Batch task executor initialized: core=4, max=16, queue=50");
        return executor;
    }
}
