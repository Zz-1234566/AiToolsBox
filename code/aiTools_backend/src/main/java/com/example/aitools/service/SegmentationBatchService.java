package com.example.aitools.service;

import com.example.aitools.dto.BatchFilePayload;

import java.util.List;

/**
 * 图片去/换背景（image-bg-remover）**批量**处理服务。
 *
 * <p><b>为什么单独一个服务</b>：{@link SegmentationService} 是纯推理服务
 * （「字节进、字节出」，不碰存储 / 不落库 / 不记历史，见其接口注释）。
 * 而批量链路除了推理，还要做「建历史 → 落产物 → 记计数 → 置任务终态」这一整套编排。
 * 把编排塞进 Controller 会违反 AGENTS.md §2「禁止在 Controller 里写业务实现」，
 * 把持久化塞进 SegmentationService 又会破坏它「纯推理」的职责边界 ——
 * 所以单独开一个批量服务承接编排，推理仍然委托给 {@link SegmentationService}。
 *
 * <p>与 {@code AiOfficeToolService} 的批量方法是同一套模式：
 * 「工具的 service 持有批量循环，逐文件处理 + 逐文件落库 + 末尾汇总」。
 */
public interface SegmentationBatchService {

    /**
     * 批量图片去 / 换背景。
     *
     * <p>逐文件处理：推理 → 落产物（写 batchId 供 completed 端点反查）→ 记历史；
     * 单文件失败不中断整批，最后统一汇总成功/失败数并把任务置为终态。
     *
     * <p>本方法**不抛异常**（除 {@code Error} 级）：内部异常会被捕获并转成任务终态，
     * 保证前端轮询一定能拿到终态，不会一直转圈。
     *
     * @param userId           发起用户
     * @param payloads         待处理文件（字节已在 Controller 侧读出）
     * @param mode             transparent / color / image
     * @param backgroundColor  mode=color 时的底色
     * @param backgroundImage  mode=image 时的背景图字节
     * @param batchId          批量任务 id（产物按它关联，前端按它反查）
     */
    void processImageBatch(Long userId, List<BatchFilePayload> payloads,
                           String mode, String backgroundColor, byte[] backgroundImage,
                           String batchId);

    /**
     * 批量任务前置失败（读文件失败 / 线程池拒绝）时把任务置为失败。
     *
     * <p>用 {@code failRemaining} 而非直接覆盖计数：已处理完的文件保留结果，
     * 只把「没处理完」的文件补成失败。
     *
     * @param batchId   批量任务 id
     * @param fileCount 本批总文件数
     */
    void failBatch(String batchId, int fileCount);
}
