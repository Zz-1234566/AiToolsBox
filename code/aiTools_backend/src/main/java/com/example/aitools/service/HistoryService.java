package com.example.aitools.service;

import com.example.aitools.dto.HistoryFileDTO;
import com.example.aitools.vo.HistoryVO;

import java.util.List;

public interface HistoryService {

    /**
     * 记录 AI 工具调用历史（写入主表 + 明细子表），返回主键 id
     */
    Long record(Long userId, Long toolId, Long modelId, String aiCode,
                String inputContent, String outputContent, Integer status, Integer duration);

    /**
     * 记录 AI 工具调用历史（主表 + 明细 + 文件子表）
     */
    void recordWithFiles(Long userId, Long toolId, Long modelId, String aiCode,
                         String inputContent, String outputContent, Integer status, Integer duration,
                         List<HistoryFileDTO> files);

    /**
     * 创建"处理中"历史记录（主表 + 明细），返回 historyId
     * 一旦 AI 被调用就调用此方法，保证必有记录
     */
    Long createPendingHistory(Long userId, Long toolId, Long modelId, String aiCode, String inputContent);

    /**
     * 创建"处理中"历史记录，额外记录用户当时的格式/生成提示词（用于历史回填原参数重发）
     * @param promptFormat    用户在格式提示词 textarea 的原值（可为 null/空）
     * @param promptGenerate  用户在生成提示词 textarea 的原值（可为 null/空）
     */
    Long createPendingHistory(Long userId, Long toolId, Long modelId, String aiCode,
                              String inputContent, String promptFormat, String promptGenerate);

    /**
     * 更新为成功（写输出 + 耗时）
     */
    void completeHistory(Long historyId, String outputContent, Integer duration);

    /**
     * 更新为失败（写错误信息）
     */
    void failHistory(Long historyId, String errorMsg);

    /**
     * 记录一次「工作流节点」的工具调用历史。
     * <p>
     * 与 {@link #createPendingHistory} 的区别：额外带上工作流来源信息
     * （sourceType=2 + runId + nodeId + nodeName），
     * 使历史记录可反查「这次是哪个工作流的哪个节点产生的」。
     * <p>
     * 设计上不用联合主键：单独跑工具时 runId 为空，主键无意义；
     * 且一条工作流跑 N 个节点会产出 N 条同 runId 的记录，联合主键必然冲突。
     *
     * @param runId    工作流运行 ID（sys_workflow_run.run_id）
     * @param nodeId   节点 ID（node_results 的键，如 n1）
     * @param nodeName 节点名称快照（如「文档提取」）
     * @return historyId，供后续 complete/fail
     */
    Long createWorkflowNodeHistory(Long userId, Long toolId, Long modelId, String aiCode,
                                   String inputContent, String runId, String nodeId, String nodeName);

    /**
     * 查询用户最近历史（组装主表+明细+文件+工具名），取 10 条
     */
    List<HistoryVO> listRecent(Long userId, int limit);

    /**
     * 查询指定用户最近历史（按 aiCode 过滤），组装主表+明细+文件+工具名
     * @param userId  用户 id（账号）
     * @param aiCode  工具编码（如 work-summary / meeting-minutes），为空则不按工具过滤
     * @param limit   返回条数上限（调用方应保证 1 <= limit <= 50）
     */
    List<HistoryVO> listRecent(Long userId, String aiCode, int limit);

    /**
     * 查询指定用户历史（按 aiCode 过滤 + 偏移量），供前端滚动加载更多
     * @param offset 跳过条数（从 0 开始）
     */
    List<HistoryVO> listRecent(Long userId, String aiCode, int limit, int offset);

    /**
     * 删除历史记录（逻辑删除主表，明细/文件子表一并逻辑删除）
     */
    void delete(Long id, Long userId);

    /**
     * 清空当前用户全部历史记录（逻辑删除主表 + 明细 + 文件子表）
     */
    void clearAll(Long userId);
}
