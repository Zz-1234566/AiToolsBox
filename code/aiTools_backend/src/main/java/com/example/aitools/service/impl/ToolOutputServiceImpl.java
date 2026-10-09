package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.FileUploadResponse;
import com.example.aitools.entity.ToolOutput;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.ToolOutputMapper;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.ToolOutputService;
import com.example.aitools.vo.ToolOutputVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具产物服务实现（对应全局工具产物表 {@code sys_tool_output}）。
 * <p>
 * 任何工具产出文件都走这里落库，不仅限于工作流节点；工作流来源时
 * {@code runId} / {@code nodeId} 有值，独立调用工具时为 null（无工作流上下文）。
 * <p>
 * 文件实体的存储前缀固定为 {@code tool-output}（Constants.TOOL_OUTPUT_PREFIX），
 * 与用户上传区（{@code file/}）隔离，便于生命周期管理与清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolOutputServiceImpl implements ToolOutputService {

    private final ToolOutputMapper workflowOutputMapper;
    private final FileStorageService fileStorageService;

    @Override
    public ToolOutputVO saveFileOutput(String runId, String nodeId, String nodeName, String toolCode,
                                           int fileIndex, int outputType, byte[] content, String fileName,
                                           String mimeType, Integer width, Integer height, Integer durationMs) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "产物内容为空");
        }
        // 1) 实体入 COS
        FileUploadResponse up = fileStorageService.store(
                content, fileName, mimeType, Constants.TOOL_OUTPUT_PREFIX);

        // 2) 元信息落库
        ToolOutput o = new ToolOutput();
        o.setRunId(runId);
        o.setNodeId(nodeId);
        o.setNodeName(nodeName);
        o.setToolCode(toolCode);
        o.setFileIndex(fileIndex);
        o.setOutputType(outputType);
        o.setFileName(up.getFileName() != null ? up.getFileName() : fileName);
        // 存 key 不存签名 URL：tool-output/ 是公开读直链，存 key 才能让「读出即签发」链路统一
        o.setFileUrl(up.getCosKey());
        o.setCosKey(up.getCosKey());
        o.setFileSize((long) content.length);
        o.setMimeType(mimeType);
        o.setWidth(width);
        o.setHeight(height);
        o.setDurationMs(durationMs);
        o.setDr(Constants.DR_NORMAL);
        workflowOutputMapper.insert(o);

        log.info("[tool-output] 产物已保存 runId={} nodeId={} idx={} type={} name={} size={}B",
                runId, nodeId, fileIndex, outputType, o.getFileName(), content.length);
        return toVO(o);
    }

    @Override
    public List<ToolOutputVO> listByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return new ArrayList<>();
        }
        List<ToolOutput> list = workflowOutputMapper.selectList(
                new LambdaQueryWrapper<ToolOutput>()
                        .eq(ToolOutput::getRunId, runId)
                        .eq(ToolOutput::getDr, Constants.DR_NORMAL)
                        .orderByAsc(ToolOutput::getNodeId)
                        .orderByAsc(ToolOutput::getFileIndex));
        List<ToolOutputVO> vos = new ArrayList<>(list.size());
        for (ToolOutput o : list) {
            vos.add(toVO(o));
        }
        return vos;
    }

    @Override
    public List<ToolOutputVO> listByRunAndNode(String runId, String nodeId) {
        if (runId == null || runId.isBlank() || nodeId == null || nodeId.isBlank()) {
            return new ArrayList<>();
        }
        List<ToolOutput> list = workflowOutputMapper.selectList(
                new LambdaQueryWrapper<ToolOutput>()
                        .eq(ToolOutput::getRunId, runId)
                        .eq(ToolOutput::getNodeId, nodeId)
                        .eq(ToolOutput::getDr, Constants.DR_NORMAL)
                        .orderByAsc(ToolOutput::getFileIndex));
        List<ToolOutputVO> vos = new ArrayList<>(list.size());
        for (ToolOutput o : list) {
            vos.add(toVO(o));
        }
        return vos;
    }

    private ToolOutputVO toVO(ToolOutput o) {
        ToolOutputVO vo = new ToolOutputVO();
        vo.setId(o.getId());
        vo.setNodeId(o.getNodeId());
        vo.setNodeName(o.getNodeName());
        vo.setToolCode(o.getToolCode());
        vo.setFileIndex(o.getFileIndex());
        vo.setOutputType(o.getOutputType());
        vo.setContent(o.getTextContent());
        vo.setFileName(o.getFileName());
        // 读出即签发：库内存 key（老数据可能是带签名的旧 URL，signUrl 内部会剥掉旧签名重签）
        String key = o.getCosKey() != null && !o.getCosKey().isBlank() ? o.getCosKey() : o.getFileUrl();
        vo.setFileUrl(fileStorageService.signUrl(key));
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
