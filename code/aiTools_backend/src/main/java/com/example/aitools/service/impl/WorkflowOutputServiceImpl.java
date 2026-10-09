package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.entity.WorkflowOutput;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.WorkflowOutputMapper;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.WorkflowOutputService;
import com.example.aitools.vo.WorkflowOutputVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流产物服务实现。
 * <p>
 * 文件实体的存储前缀固定为 {@code wf-output}（Constants.WORKFLOW_OUTPUT_PREFIX），
 * 与用户上传区（{@code file/}）隔离，便于生命周期管理与清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOutputServiceImpl implements WorkflowOutputService {

    private final WorkflowOutputMapper workflowOutputMapper;
    private final FileStorageService fileStorageService;

    @Override
    public WorkflowOutputVO saveFileOutput(String runId, String nodeId, String nodeName, String toolCode,
                                           int fileIndex, int outputType, byte[] content, String fileName,
                                           String mimeType, Integer width, Integer height, Integer durationMs) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "产物内容为空");
        }
        // 1) 实体入 COS
        FileUploadResponse up = fileStorageService.store(
                content, fileName, mimeType, Constants.WORKFLOW_OUTPUT_PREFIX);

        // 2) 元信息落库
        WorkflowOutput o = new WorkflowOutput();
        o.setRunId(runId);
        o.setNodeId(nodeId);
        o.setNodeName(nodeName);
        o.setToolCode(toolCode);
        o.setFileIndex(fileIndex);
        o.setOutputType(outputType);
        o.setFileName(up.getFileName() != null ? up.getFileName() : fileName);
        o.setFileUrl(up.getFileUrl());
        o.setCosKey(up.getCosKey());
        o.setFileSize((long) content.length);
        o.setMimeType(mimeType);
        o.setWidth(width);
        o.setHeight(height);
        o.setDurationMs(durationMs);
        o.setDr(Constants.DR_NORMAL);
        workflowOutputMapper.insert(o);

        log.info("[wf-output] 产物已保存 runId={} nodeId={} idx={} type={} name={} size={}B",
                runId, nodeId, fileIndex, outputType, o.getFileName(), content.length);
        return toVO(o);
    }

    @Override
    public List<WorkflowOutputVO> listByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return new ArrayList<>();
        }
        List<WorkflowOutput> list = workflowOutputMapper.selectList(
                new LambdaQueryWrapper<WorkflowOutput>()
                        .eq(WorkflowOutput::getRunId, runId)
                        .eq(WorkflowOutput::getDr, Constants.DR_NORMAL)
                        .orderByAsc(WorkflowOutput::getNodeId)
                        .orderByAsc(WorkflowOutput::getFileIndex));
        List<WorkflowOutputVO> vos = new ArrayList<>(list.size());
        for (WorkflowOutput o : list) {
            vos.add(toVO(o));
        }
        return vos;
    }

    @Override
    public List<WorkflowOutputVO> listByRunAndNode(String runId, String nodeId) {
        if (runId == null || runId.isBlank() || nodeId == null || nodeId.isBlank()) {
            return new ArrayList<>();
        }
        List<WorkflowOutput> list = workflowOutputMapper.selectList(
                new LambdaQueryWrapper<WorkflowOutput>()
                        .eq(WorkflowOutput::getRunId, runId)
                        .eq(WorkflowOutput::getNodeId, nodeId)
                        .eq(WorkflowOutput::getDr, Constants.DR_NORMAL)
                        .orderByAsc(WorkflowOutput::getFileIndex));
        List<WorkflowOutputVO> vos = new ArrayList<>(list.size());
        for (WorkflowOutput o : list) {
            vos.add(toVO(o));
        }
        return vos;
    }

    private WorkflowOutputVO toVO(WorkflowOutput o) {
        WorkflowOutputVO vo = new WorkflowOutputVO();
        vo.setId(o.getId());
        vo.setNodeId(o.getNodeId());
        vo.setNodeName(o.getNodeName());
        vo.setToolCode(o.getToolCode());
        vo.setFileIndex(o.getFileIndex());
        vo.setOutputType(o.getOutputType());
        vo.setContent(o.getTextContent());
        vo.setFileName(o.getFileName());
        vo.setFileUrl(o.getFileUrl());
        vo.setFileSize(o.getFileSize());
        vo.setFileSizeText(humanSize(o.getFileSize()));
        vo.setMimeType(o.getMimeType());
        vo.setWidth(o.getWidth());
        vo.setHeight(o.getHeight());
        vo.setDurationMs(o.getDurationMs());
        if (o.getCreateTime() != null) {
            vo.setCreateTime(o.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    /** 人类可读体积，如 1.2 MB */
    private String humanSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return null;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
