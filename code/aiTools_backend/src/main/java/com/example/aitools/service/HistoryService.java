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
     * 更新为成功（写输出 + 耗时）
     */
    void completeHistory(Long historyId, String outputContent, Integer duration);

    /**
     * 更新为失败（写错误信息）
     */
    void failHistory(Long historyId, String errorMsg);

    /**
     * 查询用户最近历史（组装主表+明细+文件+工具名），取 10 条
     */
    List<HistoryVO> listRecent(Long userId, int limit);

    /**
     * 删除历史记录（逻辑删除主表，明细/文件子表一并逻辑删除）
     */
    void delete(Long id, Long userId);

    /**
     * 清空当前用户全部历史记录（逻辑删除主表 + 明细 + 文件子表）
     */
    void clearAll(Long userId);
}
