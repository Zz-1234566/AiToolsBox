package com.example.aitools.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
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

    /**
     * 批量任务池的<b>最大线程数</b>（= 同时处理的重任务数上限）。
     *
     * <p><b>为什么必须限制</b>：批量任务里最吃内存的不是上传（文件已流式落盘），
     * 而是<b>文档解析 / PDF 渲染</b>。按 2GB 堆估算单个重任务的峰值堆：
     * <pre>
     *   单文件字节（max-file-size）              20.0 MB
     *   PDF 渲染单页 BufferedImage(150dpi A4)    8.7 MB
     *   PDF 累计 PNG（上限 20 页，照片页 2.5MB） 50.0 MB
     *   杂项（文本 / AI 缓冲 / JSON / 历史）      5.0 MB
     *   ------------------------------------------------
     *   单任务峰值                              83.7 MB
     * </pre>
     * 2GB 堆的预算：总 2048MB − 预留 800MB（GC 复制/晋升、元空间、线程栈、
     * SSE 流式缓冲、非批量请求、Tomcat 缓冲）= 1248MB 可用于批量并发
     * → 理论上限 1248 / 83.7 ≈ 14.9，<b>再取 2 倍安全系数 → 7~8，取 8</b>。
     * <p>8 × 83.7MB = 670MB（占堆 32.7%），其余 1.4GB 留给 GC 与其它流量。
     *
     * <p>⚠️ 这里限制的是<b>线程池最大线程数</b>，不是「队列长度」——
     * {@code ThreadPoolExecutor} 是「先入队、队满才扩线程」，
     * 不限制 maxPoolSize 的话，高负载下会自动涨到默认的 16
     * （16 × 83.7 = 1340MB，占 2GB 堆的 65%，加上 GC 余量很容易 OOM）。
     *
     * <p>可通过配置调整：{@code batch.max-concurrent-tasks}。
     * 换堆大小后应按上面的公式重算：{@code 并发 = (堆 - 预留) / 单任务峰值 / 2}。
     */
    @Value("${batch.max-concurrent-tasks:8}")
    private int batchMaxConcurrentTasks;

    /**
     * 批量任务队列深度。默认 20。
     *
     * <p><b>为什么刻意收窄</b>：排队中的任务**各自占着已落盘的上传文件**
     * （最坏 200MB/任务，因为文件要先落盘才能跨异步边界）。
     * 原先 queue=50 → 最坏 50 × 200MB ≈ <b>10GB 临时磁盘占用</b>，
     * 而临时目录在系统盘上，撑爆会连带影响整个服务。
     * 现在 queue=20 → 最坏约 4GB，且「接收上限 = 运行 8 + 排队 20 = 28 个批量任务」，
     * 超出直接返回 503（前端提示「服务繁忙」），不会无限堆积。
     *
     * <p><b>可以按部署机性能调大</b>：磁盘更大 / IO 更快时，
     * 调 {@code batch.task-queue-capacity} 即可（同时也要保证临时目录所在卷有余量）。
     * 注意：<b>不要盲目调大</b> —— 队列深度直接换算成临时磁盘占用。
     */
    @Value("${batch.task-queue-capacity:20}")
    private int batchQueueCapacity;

    @Bean(name = STREAM_EXECUTOR)
    public ExecutorService streamExecutor() {
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
        return executor.getThreadPoolExecutor();
    }

    @Bean(name = BATCH_EXECUTOR)
    public ExecutorService batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // core = max：让「同时处理的重任务数」恒等于按堆内存算出来的值。
        //   若 core < max，ThreadPoolExecutor 是「先入队、队满才扩线程」，
        //   队列没满之前并发永远只有 core 个，达不到按内存算出的上限。
        executor.setCorePoolSize(batchMaxConcurrentTasks);
        executor.setMaxPoolSize(batchMaxConcurrentTasks);
        // 队列深度：刻意收窄。原为 50，但**排队中的任务各自占着已落盘的上传文件**
        // （最坏 200MB/任务），50 × 200MB ≈ 10GB 临时磁盘占用。
        // 20 → 最坏约 4GB，且「接收上限 = 运行 8 + 排队 20 = 28 个批量任务」，
        // 超出直接 503（前端会提示「服务繁忙」），不会无限堆积。
        // 部署机磁盘更大 / IO 更快时可调大：batch.task-queue-capacity。
        executor.setQueueCapacity(batchQueueCapacity);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("batch-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Batch task executor initialized: core={}, max={}, queue={} "
                        + "(并发由堆内存算出；队列深度受临时磁盘占用约束)",
                batchMaxConcurrentTasks, batchMaxConcurrentTasks, batchQueueCapacity);
        return executor.getThreadPoolExecutor();
    }
}
