package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.dto.HistoryFileDTO;
import com.example.aitools.entity.AiTool;
import com.example.aitools.entity.History;
import com.example.aitools.entity.HistoryDetail;
import com.example.aitools.entity.HistoryFile;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.mapper.HistoryDetailMapper;
import com.example.aitools.mapper.HistoryFileMapper;
import com.example.aitools.mapper.HistoryMapper;
import com.example.aitools.service.HistoryService;
import com.example.aitools.vo.HistoryFileVO;
import com.example.aitools.vo.HistoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final HistoryMapper historyMapper;
    private final HistoryDetailMapper historyDetailMapper;
    private final HistoryFileMapper historyFileMapper;
    private final AiToolMapper aiToolMapper;

    @Override
    public Long record(Long userId, Long toolId, Long modelId, String aiCode,
                       String inputContent, String outputContent, Integer status, Integer duration) {
        History history = new History();
        history.setUserId(userId);
        history.setToolId(toolId);
        history.setModelId(modelId);
        history.setAiCode(aiCode);
        history.setStatus(status);
        history.setDuration(duration);
        history.setDr(Constants.DR_NORMAL);
        historyMapper.insert(history);

        // 写明细子表
        if (inputContent != null || outputContent != null) {
            HistoryDetail detail = new HistoryDetail();
            detail.setHistoryId(history.getId());
            detail.setInputContent(inputContent);
            detail.setOutputContent(outputContent);
            detail.setDr(Constants.DR_NORMAL);
            historyDetailMapper.insert(detail);
        }

        return history.getId();
    }

    @Override
    public void recordWithFiles(Long userId, Long toolId, Long modelId, String aiCode,
                                String inputContent, String outputContent, Integer status, Integer duration,
                                List<HistoryFileDTO> files) {
        Long historyId = record(userId, toolId, modelId, aiCode, inputContent, outputContent, status, duration);
        if (files != null) {
            for (HistoryFileDTO f : files) {
                HistoryFile file = new HistoryFile();
                BeanUtils.copyProperties(f, file);
                file.setHistoryId(historyId);
                file.setDr(Constants.DR_NORMAL);
                historyFileMapper.insert(file);
            }
        }
    }

    @Override
    public Long createPendingHistory(Long userId, Long toolId, Long modelId, String aiCode, String inputContent) {
        return createPendingHistory(userId, toolId, modelId, aiCode, inputContent, null, null);
    }

    @Override
    public Long createPendingHistory(Long userId, Long toolId, Long modelId, String aiCode,
                                     String inputContent, String promptFormat, String promptGenerate) {
        History history = new History();
        history.setUserId(userId);
        history.setToolId(toolId);
        history.setModelId(modelId);
        history.setAiCode(aiCode);
        history.setStatus(0); // 处理中
        history.setDr(Constants.DR_NORMAL);
        historyMapper.insert(history);

        HistoryDetail detail = new HistoryDetail();
        detail.setHistoryId(history.getId());
        detail.setInputContent(inputContent);
        detail.setPromptFormat(promptFormat);
        detail.setPromptGenerate(promptGenerate);
        detail.setDr(Constants.DR_NORMAL);
        historyDetailMapper.insert(detail);

        return history.getId();
    }

    @Override
    public void completeHistory(Long historyId, String outputContent, Integer duration) {
        // 更新主表 status=1 + duration
        History history = new History();
        history.setId(historyId);
        history.setStatus(1);
        history.setDuration(duration);
        historyMapper.updateById(history);

        // 更新明细 outputContent
        LambdaUpdateWrapper<HistoryDetail> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HistoryDetail::getHistoryId, historyId)
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .set(HistoryDetail::getOutputContent, outputContent);
        historyDetailMapper.update(null, wrapper);
    }

    @Override
    public void failHistory(Long historyId, String errorMsg) {
        // 主表置为明确的失败态（2），与「处理中」（0）区分，避免失败记录永久显示为处理中。
        // 前端历史页以 status==1 判定成功，2/0 均显示失败，故兼容。
        LambdaUpdateWrapper<History> mainWrapper = new LambdaUpdateWrapper<>();
        mainWrapper.eq(History::getId, historyId)
                .eq(History::getDr, Constants.DR_NORMAL)
                .set(History::getStatus, Constants.HISTORY_STATUS_FAILED);
        historyMapper.update(null, mainWrapper);

        // 更新明细 errorMsg
        LambdaUpdateWrapper<HistoryDetail> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HistoryDetail::getHistoryId, historyId)
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .set(HistoryDetail::getErrorMsg, errorMsg);
        historyDetailMapper.update(null, wrapper);
    }

    @Override
    public List<HistoryVO> listRecent(Long userId, int limit) {
        return listRecent(userId, null, limit);
    }

    @Override
    public List<HistoryVO> listRecent(Long userId, String aiCode, int limit) {
        return listRecent(userId, aiCode, limit, 0);
    }

    @Override
    public List<HistoryVO> listRecent(Long userId, String aiCode, int limit, int offset) {
        // 1) 主查询：取最近 limit 条历史（offset 用于滚动加载更多）
        //    aiCode 为空时不过滤,等同于按用户拉全部
        //    create_time 精度到秒，同秒记录顺序不稳定会导致翻页错乱，故用 id 兜底排序
        LambdaQueryWrapper<History> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(History::getUserId, userId)
                .eq(History::getDr, Constants.DR_NORMAL)
                .eq(aiCode != null && !aiCode.isEmpty(), History::getAiCode, aiCode)
                .orderByDesc(History::getCreateTime)
                .orderByDesc(History::getId)
                .last("LIMIT " + limit + " OFFSET " + Math.max(offset, 0));
        List<History> histories = historyMapper.selectList(wrapper);
        if (histories.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 2) 批量预加载（解决 P2-B10 N+1：100 条历史从 301 SQL 降到 4 SQL）
        List<Long> historyIds = histories.stream().map(History::getId).toList();
        List<Long> toolIds = histories.stream().map(History::getToolId).filter(java.util.Objects::nonNull).distinct().toList();

        // tool: toolId -> AiTool
        Map<Long, AiTool> toolMap = toolIds.isEmpty() ? java.util.Collections.emptyMap()
                : aiToolMapper.selectBatchIds(toolIds).stream()
                .collect(Collectors.toMap(AiTool::getId, t -> t, (a, b) -> a));

        // detail: historyId -> HistoryDetail（每条 history 取一条明细）
        Map<Long, HistoryDetail> detailMap = new java.util.HashMap<>();
        List<HistoryDetail> allDetails = historyDetailMapper.selectList(
                new LambdaQueryWrapper<HistoryDetail>()
                        .in(HistoryDetail::getHistoryId, historyIds)
                        .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                        .orderByAsc(HistoryDetail::getId));  // 取最早一条
        for (HistoryDetail d : allDetails) {
            detailMap.putIfAbsent(d.getHistoryId(), d);
        }

        // file: historyId -> List<HistoryFile>
        Map<Long, List<HistoryFile>> fileMap = new java.util.HashMap<>();
        List<HistoryFile> allFiles = historyFileMapper.selectList(
                new LambdaQueryWrapper<HistoryFile>()
                        .in(HistoryFile::getHistoryId, historyIds)
                        .eq(HistoryFile::getDr, Constants.DR_NORMAL));
        for (HistoryFile f : allFiles) {
            fileMap.computeIfAbsent(f.getHistoryId(), k -> new java.util.ArrayList<>()).add(f);
        }

        // 3) 组装 VO
        return histories.stream().map(h -> toVO(h, toolMap, detailMap, fileMap)).toList();
    }

    private HistoryVO toVO(History h, Map<Long, AiTool> toolMap, Map<Long, HistoryDetail> detailMap, Map<Long, List<HistoryFile>> fileMap) {
        HistoryVO vo = new HistoryVO();
        vo.setId(h.getId());
        vo.setUserId(h.getUserId());
        vo.setToolId(h.getToolId());
        vo.setModelId(h.getModelId());
        vo.setAiCode(h.getAiCode());
        vo.setStatus(h.getStatus());
        vo.setDuration(h.getDuration());
        vo.setCreateTime(h.getCreateTime());

        // 工具名（从批量预加载的 map 查，O(1)）
        if (h.getToolId() != null) {
            AiTool tool = toolMap.get(h.getToolId());
            if (tool != null) {
                vo.setToolName(tool.getToolName());
            }
        }

        // 明细
        HistoryDetail detail = detailMap.get(h.getId());
        if (detail != null) {
            vo.setInputContent(detail.getInputContent());
            vo.setOutputContent(detail.getOutputContent());
            vo.setErrorMsg(detail.getErrorMsg());
            vo.setPromptFormat(detail.getPromptFormat());
            vo.setPromptGenerate(detail.getPromptGenerate());
        }

        // 文件
        List<HistoryFile> files = fileMap.getOrDefault(h.getId(), java.util.Collections.emptyList());
        vo.setFiles(files.stream().map(f -> {
            HistoryFileVO fvo = new HistoryFileVO();
            fvo.setId(f.getId());
            fvo.setFileId(f.getFileId());
            fvo.setFileName(f.getFileName());
            fvo.setFileUrl(f.getFileUrl());
            fvo.setFileType(f.getFileType());
            fvo.setRole(f.getRole());
            return fvo;
        }).toList());

        return vo;
    }

    @Override
    public void delete(Long id, Long userId) {
        // 校验归属并逻辑删除主表
        LambdaUpdateWrapper<History> historyWrapper = new LambdaUpdateWrapper<>();
        historyWrapper.eq(History::getId, id)
                .eq(History::getUserId, userId)
                .eq(History::getDr, Constants.DR_NORMAL)
                .set(History::getDr, Constants.DR_DELETED);
        historyMapper.update(null, historyWrapper);

        // 逻辑删除明细
        LambdaUpdateWrapper<HistoryDetail> detailWrapper = new LambdaUpdateWrapper<>();
        detailWrapper.eq(HistoryDetail::getHistoryId, id)
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .set(HistoryDetail::getDr, Constants.DR_DELETED);
        historyDetailMapper.update(null, detailWrapper);

        // 逻辑删除文件
        LambdaUpdateWrapper<HistoryFile> fileWrapper = new LambdaUpdateWrapper<>();
        fileWrapper.eq(HistoryFile::getHistoryId, id)
                .eq(HistoryFile::getDr, Constants.DR_NORMAL)
                .set(HistoryFile::getDr, Constants.DR_DELETED);
        historyFileMapper.update(null, fileWrapper);
    }

    @Override
    public void clearAll(Long userId) {
        // 查询该用户所有未删除主表记录 id 列表
        LambdaQueryWrapper<History> historyQuery = new LambdaQueryWrapper<>();
        historyQuery.eq(History::getUserId, userId)
                .eq(History::getDr, Constants.DR_NORMAL);
        List<History> histories = historyMapper.selectList(historyQuery);
        List<Long> ids = histories.stream().map(History::getId).toList();

        // 逻辑删除主表
        LambdaUpdateWrapper<History> historyWrapper = new LambdaUpdateWrapper<>();
        historyWrapper.eq(History::getUserId, userId)
                .eq(History::getDr, Constants.DR_NORMAL)
                .set(History::getDr, Constants.DR_DELETED);
        historyMapper.update(null, historyWrapper);

        // 明细/文件子表按主表 id 一并逻辑删除
        if (!ids.isEmpty()) {
            LambdaUpdateWrapper<HistoryDetail> detailWrapper = new LambdaUpdateWrapper<>();
            detailWrapper.in(HistoryDetail::getHistoryId, ids)
                    .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                    .set(HistoryDetail::getDr, Constants.DR_DELETED);
            historyDetailMapper.update(null, detailWrapper);

            LambdaUpdateWrapper<HistoryFile> fileWrapper = new LambdaUpdateWrapper<>();
            fileWrapper.in(HistoryFile::getHistoryId, ids)
                    .eq(HistoryFile::getDr, Constants.DR_NORMAL)
                    .set(HistoryFile::getDr, Constants.DR_DELETED);
            historyFileMapper.update(null, fileWrapper);
        }
    }
}
