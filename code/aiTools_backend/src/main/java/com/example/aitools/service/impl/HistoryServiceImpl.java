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
        // 主表 status 保持 0（处理中即失败）
        // 更新明细 errorMsg
        LambdaUpdateWrapper<HistoryDetail> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HistoryDetail::getHistoryId, historyId)
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .set(HistoryDetail::getErrorMsg, errorMsg);
        historyDetailMapper.update(null, wrapper);
    }

    @Override
    public List<HistoryVO> listRecent(Long userId, int limit) {
        LambdaQueryWrapper<History> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(History::getUserId, userId)
                .eq(History::getDr, Constants.DR_NORMAL)
                .orderByDesc(History::getCreateTime)
                .last("LIMIT " + limit);
        List<History> histories = historyMapper.selectList(wrapper);
        return histories.stream().map(this::toVO).toList();
    }

    private HistoryVO toVO(History h) {
        HistoryVO vo = new HistoryVO();
        vo.setId(h.getId());
        vo.setUserId(h.getUserId());
        vo.setToolId(h.getToolId());
        vo.setModelId(h.getModelId());
        vo.setAiCode(h.getAiCode());
        vo.setStatus(h.getStatus());
        vo.setDuration(h.getDuration());
        vo.setCreateTime(h.getCreateTime());

        // 联查工具名
        if (h.getToolId() != null) {
            AiTool tool = aiToolMapper.selectById(h.getToolId());
            if (tool != null) {
                vo.setToolName(tool.getToolName());
            }
        }

        // 查明细
        LambdaQueryWrapper<HistoryDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(HistoryDetail::getHistoryId, h.getId())
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .last("LIMIT 1");
        HistoryDetail detail = historyDetailMapper.selectOne(detailWrapper);
        if (detail != null) {
            vo.setInputContent(detail.getInputContent());
            vo.setOutputContent(detail.getOutputContent());
            vo.setErrorMsg(detail.getErrorMsg());
        }

        // 查文件
        LambdaQueryWrapper<HistoryFile> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(HistoryFile::getHistoryId, h.getId())
                .eq(HistoryFile::getDr, Constants.DR_NORMAL);
        List<HistoryFile> files = historyFileMapper.selectList(fileWrapper);
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
