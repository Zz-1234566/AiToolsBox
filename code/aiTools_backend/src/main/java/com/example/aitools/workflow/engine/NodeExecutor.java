package com.example.aitools.workflow.engine;

import com.example.aitools.ai.AiClient;
import com.example.aitools.common.NodeIoTypeEnum;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.OcrService;
import com.example.aitools.service.TranscribeService;
import com.example.aitools.service.document.DocumentParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点执行器：把一个节点（工具）执行 N 次，返回 N 个输出。
 * <p>
 * <b>定位</b>：只依赖「能力层」（TranscribeService / DocumentParser / OcrService / AiClient），
 * 不依赖 Controller / Handler，与现有工具入口是平级的消费者，互不影响。
 * <p>
 * <b>数组语义</b>：inputs 为上游输出列表。
 * <ul>
 *   <li>源节点（无上游）：inputs 长度 1（用户填的文本或上传的单个文件）</li>
 *   <li>汇聚节点（多上游）：inputs 长度 N，逐项独立处理，输出 N 个结果，不混在一起</li>
 * </ul>
 * 单输入即 N=1 的特例，故统一成一个签名。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeExecutor {

    private final TranscribeService transcribeService;
    private final DocumentParser documentParser;
    private final OcrService ocrService;
    private final AiClient aiClient;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiToolMapper aiToolMapper;

    /**
     * 执行节点：N 进 N 出。
     *
     * @param toolCode 节点对应的工具编码（sys_aitools_tool.tool_code）
     * @param inputs   输入列表。文本节点为文本内容；文件节点为文件路径/URL 标识（由调用方落盘后传入）
     * @param params   节点参数（promptFormat / promptGenerate / promptId 等），可为 null
     * @return 输出列表，与 inputs 等长（逐项处理）
     */
    public List<String> execute(String toolCode, List<String> inputs, Map<String, String> params) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点缺少输入内容，请检查上游节点配置");
        }
        AiTool tool = findByCode(toolCode);
        String inputType = firstInputType(tool.getInputType());

        List<String> outputs = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            String input = inputs.get(i);
            String out = executeOne(toolCode, inputType, input, params);
            outputs.add(out);
            log.info("[workflow-node] toolCode={} inputType={} [{}/{}] outLen={}",
                    toolCode, inputType, i + 1, inputs.size(), out == null ? 0 : out.length());
        }
        return outputs;
    }

    /** 逐项执行：按该工具支持的输入类型分派到能力层 */
    private String executeOne(String toolCode, String inputType, String input, Map<String, String> params) {
        if (input == null || input.isBlank()) {
            return "";
        }
        switch (inputType == null ? "" : inputType) {
            case "audio":
                return transcribeService.transcribe(toMultipart(input, "audio.mp3")).getText();
            case "document":
            case "file":
                return documentParser.parse(toMultipart(input, fileNameOf(input)));
            case "image":
                return ocrService.recognizeText(toMultipart(input, fileNameOf(input)));
            case "text":
                return aiText(toolCode, input, params);
            case "none":
                throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "该工具不能作为工作流节点执行");
            default:
                throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "不支持的节点输入类型");
        }
    }

    /**
     * 文本节点：走 AI（复用公共提示词解析，与工具入口同一套逻辑）
     */
    private String aiText(String toolCode, String content, Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;

        // 提示词来源三选一（优先级从高到低）：
        //   1) 直接文本（兼容旧数据 / 工具入口直接传文本）
        //   2) 选中的系统/用户提示词（只存 id，跟随提示词更新）
        //   3) 该工具的系统默认提示词（用户未配置时）
        // 注意：若用户「选了具体提示词(id)」但该提示词已被删除 → 置空并报错，不静默回退，
        //       避免用户以为自己在用某条提示词、实际却跑的是默认。
        String formatPrompt = resolveNodePrompt(
                p.get("promptFormat"), parseLong(p.get("promptIdFormat")), "format", toolCode);
        String generatePrompt = resolveNodePrompt(
                p.get("promptGenerate"), parseLong(p.get("promptIdGenerate")), "generate", toolCode);

        if (isBlank(formatPrompt)) {
            throw new BusinessException(ResultCode.PROMPT_INVALID.getCode(), "节点的格式提示词无效或已被删除，请重新选择");
        }
        if (isBlank(generatePrompt)) {
            throw new BusinessException(ResultCode.PROMPT_INVALID.getCode(), "节点的生成提示词无效或已被删除，请重新选择");
        }
        String userPrompt = generatePrompt + "\n\n原文：\n" + content;
        return aiClient.chat(formatPrompt, userPrompt);
    }

    /**
     * 解析节点提示词。
     *
     * @param userProvided 直接填写的提示词文本（可空）
     * @param promptId     选中的提示词 id（可空）；非空但查不到 → 返回 null，由调用方报错
     * @param promptUse    format / generate
     * @param toolCode     工具编码（用于回退系统默认）
     * @return 提示词内容；无法解析时返回 null
     */
    private String resolveNodePrompt(String userProvided, Long promptId, String promptUse, String toolCode) {
        // 1) 直接文本优先
        if (userProvided != null && !userProvided.isBlank()) {
            return userProvided.trim();
        }
        // 2) 选了具体提示词：按 id 取，用途需匹配；查不到/异常 → 置空（不静默回退）
        if (promptId != null) {
            try {
                com.example.aitools.entity.AiPrompt selected = aiPromptTemplateService.getById(promptId);
                if (selected != null && promptUse.equals(selected.getPromptUse())) {
                    return selected.getPromptContent();
                }
            } catch (Exception e) {
                log.warn("[workflow-node] 提示词不可用 promptId={} use={}: {}", promptId, promptUse, e.getMessage());
            }
            return null;
        }
        // 3) 未配置：回退该系统默认提示词
        com.example.aitools.entity.AiPrompt def = aiPromptTemplateService.getDefaultByUse(toolCode, promptUse);
        return def == null ? null : def.getPromptContent();
    }

    /**
     * 文件标识 → MultipartFile。
     * <p>
     * 说明：当前 FileStorageService 只提供 store（无读回），工作流采用「运行时上传」方案，
     * 因此输入直接是上传文件的本地路径或 data URL（H5），此处统一包装成内存版 MultipartFile。
     * 预留：后续如需支持"历史文件复用"，在此处改为从存储服务读回。
     */
    private org.springframework.web.multipart.MultipartFile toMultipart(String pathOrDataUrl, String fallbackName) {
        try {
            // 1) data URL（data:application/pdf;base64,xxxx）→ 解码
            if (pathOrDataUrl.startsWith("data:")) {
                int comma = pathOrDataUrl.indexOf(',');
                if (comma < 0) throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点输入数据格式非法");
                String meta = pathOrDataUrl.substring(5, comma);
                byte[] bytes = java.util.Base64.getDecoder().decode(pathOrDataUrl.substring(comma + 1));
                String name = meta.contains("/") ? meta.substring(meta.indexOf('/') + 1).split(";")[0] : fallbackName;
                return payloadToMultipart(new BatchFilePayload(bytes, name));
            }
            // 2) HTTP(S) URL（如 COS 签名地址）→ 下载字节
            //    前端上传文件后拿到的是 COS 的 https 地址，不能当本地路径处理
            if (pathOrDataUrl.startsWith("http://") || pathOrDataUrl.startsWith("https://")) {
                byte[] bytes = downloadBytes(pathOrDataUrl);
                if (bytes.length == 0) {
                    throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点输入文件为空，请重新上传");
                }
                if (bytes.length > Constants.BATCH_SINGLE_FILE_MAX_SIZE) {
                    throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "节点输入文件超过单文件大小上限（20MB）");
                }
                return payloadToMultipart(new BatchFilePayload(bytes, fileNameOf(pathOrDataUrl)));
            }
            // 3) 本地文件路径
            java.io.File f = new java.io.File(pathOrDataUrl);
            if (!f.exists() || !f.isFile()) {
                // 脱敏：pathOrDataUrl 可能是服务器本地路径；仅日志记录，不透给前端
                log.warn("节点输入文件不存在: {}", pathOrDataUrl);
                throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点输入文件不存在，请重新上传");
            }
            if (f.length() > Constants.BATCH_SINGLE_FILE_MAX_SIZE) {
                throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "节点输入文件超过单文件大小上限（20MB）");
            }
            return payloadToMultipart(new BatchFilePayload(java.nio.file.Files.readAllBytes(f.toPath()), f.getName()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 脱敏：NoSuchFileException 等 message 含服务器绝对路径，只进日志
            log.error("读取节点输入文件失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "读取节点输入文件失败，请重新上传");
        }
    }

    /** 下载 http(s) 资源字节（COS 签名 URL 等），失败抛业务异常 */
    private byte[] downloadBytes(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new BusinessException(ResultCode.FILE_NOT_FOUND.getCode(), "节点输入文件下载失败，可能链接已过期，请重新上传");
            }
            try (java.io.InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 脱敏：HttpURLConnection 异常 message 含完整 COS 签名 URL（含签名参数），只进日志
            log.error("节点输入文件下载失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点输入文件下载失败，可能链接已过期，请重新上传");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 复用 BatchFilePayload 已有的内存版 MultipartFile 适配（避免重复实现） */
    private org.springframework.web.multipart.MultipartFile payloadToMultipart(BatchFilePayload payload) {
        return payload.toMultipartFile();
    }

    private String fileNameOf(String path) {
        if (path == null) return "file";
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return slash >= 0 && slash < p.length() - 1 ? p.substring(slash + 1) : "file";
    }

    private AiTool findByCode(String toolCode) {
        LambdaQueryWrapper<AiTool> w = new LambdaQueryWrapper<>();
        w.eq(AiTool::getToolCode, toolCode).last("LIMIT 1");
        AiTool tool = aiToolMapper.selectOne(w);
        if (tool == null) {
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "节点引用的工具不存在，请重新选择工具");
        }
        return tool;
    }

    /** 取工具输入类型集合中的第一个（工作流节点按首个类型分派执行） */
    private String firstInputType(String inputTypes) {
        if (inputTypes == null || inputTypes.isBlank()) return null;
        return Arrays.stream(inputTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /** 供引擎做连线类型校验：上游输出类型能否被下游输入类型集合接住 */
    public boolean canConnect(String upstreamOutputType, String downstreamInputTypes) {
        return NodeIoTypeEnum.canConnect(upstreamOutputType, downstreamInputTypes);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
