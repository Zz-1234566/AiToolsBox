package com.example.aitools.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p><b>为什么单独一个类</b>：{@link ExecutorConfig} 管的是「线程池」，
 * 定时调度是另一件事（调度器自己也有线程池）。分开后关掉定时任务只需注释掉本类，
 * 不会牵连业务线程池。
 *
 * <p>目前唯一的定时任务是 {@code BatchTaskRecoveryJob}：
 * 扫描进程崩溃后僵死在 PENDING / RUNNING 的批量任务并置为失败，
 * 避免前端永远轮询不到终态、僵尸行永久堆积。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
