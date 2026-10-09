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
import com.example.aitools.service.FileStorageService;
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
    private final FileStorageService fileStorageService;

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
            detail.setInputContent(truncate(inputContent, Constants.AI_INPUT_MAX_LENGTH));
            detail.setOutputContent(truncate(outputContent, Constants.AI_OUTPUT_MAX_LENGTH));
            detail.setDr(Constants.DR_NORMAL);
            historyDetailMapper.insert(detail);
        }

        return history.getId();
    }

    /**
     * 落库前截断，防止超长内容写满 MySQL {@code text} 的 65535 字节。
     * <p>
     * 阈值按 <b>UTF-16 长度</b>（{@code String.length()}）判定 ——
     * emoji / 生僻字占 2 个 char，这样能保证实际 UTF-8 字节数不超过
     * {@code 阈值 × 4}（{@code AI_OUTPUT_MAX_LENGTH=16000} → 最坏 64000 字节，安全）。
     * <p>
     * 切分点用 {@code offsetByCodePoints} 求出，保证不从代理对中间切开产生乱码。
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        if (text.length() <= max) {
            return text;
        }
        int end = text.offsetByCodePoints(0, Math.min(max, text.codePointCount(0, text.length())));
        return text.substring(0, end) + Constants.TRUNCATE_SUFFIX;
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
                // 存对象 key 而非签名 URL：签名 URL 是一次性凭证（默认几分钟过期），
                // 持久化后必然失效；查询时统一实时签发，历史记录才能长期可用。
                file.setFileUrl(FileStorageService.toObjectKey(file.getFileUrl()));
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
        detail.setInputContent(truncate(inputContent, Constants.AI_INPUT_MAX_LENGTH));
        detail.setPromptFormat(promptFormat);
        detail.setPromptGenerate(promptGenerate);
        detail.setDr(Constants.DR_NORMAL);
        historyDetailMapper.insert(detail);

        return history.getId();
    }

    @Override
    public Long createWorkflowNodeHistory(Long userId, Long toolId, Long modelId, String aiCode,
                                          String inputContent, String runId, String nodeId, String nodeName) {
        History history = new History();
        history.setUserId(userId);
        history.setToolId(toolId);
        history.setModelId(modelId);
        history.setAiCode(aiCode);
        history.setSourceType(Constants.HISTORY_SOURCE_WORKFLOW);
        history.setRunId(runId);
        history.setNodeId(nodeId);
        history.setNodeName(nodeName);
        history.setStatus(Constants.HISTORY_STATUS_PROCESSING);
        history.setDr(Constants.DR_NORMAL);
        historyMapper.insert(history);

        HistoryDetail detail = new HistoryDetail();
        detail.setHistoryId(history.getId());
        detail.setInputContent(truncate(inputContent, Constants.AI_INPUT_MAX_LENGTH));
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

        // 更新明细 outputContent（截断防 text 字段溢出）
        LambdaUpdateWrapper<HistoryDetail> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HistoryDetail::getHistoryId, historyId)
                .eq(HistoryDetail::getDr, Constants.DR_NORMAL)
                .set(HistoryDetail::getOutputContent, truncate(outputContent, Constants.AI_OUTPUT_MAX_LENGTH));
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
            vo.setInputContent(renewSignedUrls(detail.getInputContent()));
            vo.setOutputContent(renewSignedUrls(detail.getOutputContent()));
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
            // 读出即签发：库内存 key；老数据是已过期的签名 URL，signUrl 内部剥掉旧签名后重签
            fvo.setFileUrl(fileStorageService.signUrl(f.getFileUrl()));
            fvo.setFileType(f.getFileType());
            fvo.setRole(f.getRole());
            return fvo;
        }).toList());

        return vo;
    }

    /**
     * 把文本里已过期的 COS 签名 URL 换成重新签发的新 URL。
     * <p>
     * 背景：部分历史记录的 {@code input_content}/{@code output_content} 里
     * 直接存了整条签名 URL（如上传音频后把地址当输入内容提交），
     * 签名到期后前端点开就是 403。存量数据不迁移，而是在读取时顺手续签，
     * 保证老记录同样能打开。
     * <p>
     * 只替换「带 {@code ?sign=} 的本桶 https 地址」，普通文本里的链接、换行、
     * 中文标点原样保留；未命中时直接返回原字符串（零拷贝）。
     */
    private String renewSignedUrls(String text) {
        if (text == null || text.isEmpty() || !text.contains("?sign=")) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 64);
        java.util.regex.Matcher m = SIGNED_URL_PATTERN.matcher(text);
        int last = 0;
        boolean replaced = false;
        while (m.find()) {
            String renewed = fileStorageService.signUrl(m.group());
            // 本地存储无签名概念，signUrl 会返回 /uploads/xxx → 不是等价替换，保持原值
            if (renewed != null && renewed.startsWith("http")) {
                sb.append(text, last, m.start()).append(renewed);
                last = m.end();
                replaced = true;
            }
        }
        if (!replaced) {
            return text;
        }
        return sb.append(text, last, text.length()).toString();
    }

    /** 匹配带签名的 COS 对象地址：https://{bucket}.cos.{region}.myqcloud.com/{key}?sign=... */
    private static final java.util.regex.Pattern SIGNED_URL_PATTERN = java.util.regex.Pattern.compile(
            "https?://[A-Za-z0-9._-]+\\.cos\\.[A-Za-z0-9-]+\\.myqcloud\\.com/[^\\s\"'<>?]+\\?sign=[^\\s\"'<>]+");

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
