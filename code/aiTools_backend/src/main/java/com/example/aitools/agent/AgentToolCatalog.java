package com.example.aitools.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.entity.AiTool;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.OcrService;
import com.example.aitools.service.TranscribeService;
import com.example.aitools.service.document.DocumentParser;
import com.example.aitools.workflow.engine.NodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 智能体可用工具目录。
 *
 * <p><b>工具清单从 DB 生成</b>（{@code sys_aitools_tool}）——
 * 表里已经有 {@code description} + {@code input_type} + {@code output_type}，
 * 这三样拼起来就是 LLM 需要的工具说明表，**不必再维护一份代码里的注册表**。
 *
 * <p><b>但「怎么调」不能靠 input_type 猜</b>：工作流的 {@code NodeExecutor} 是
 * 按 {@code input_type} 单类型分派的，所以
 * {@code doc-keypoint-extract}（input_type=file）在工作流里**只解析文档、不做重点提取**。
 * 智能体要的是「解析 + 提取」，所以这里给每个工具显式声明一条**管线**：
 * <pre>
 *   doc-to-text           → [parse]
 *   doc-keypoint-extract  → [parse, process]
 *   work-summary          → [process]
 *   audio-transcribe      → [transcribe]
 *   ocr-recognize         → [ocr]
 * </pre>
 *
 * <p><b>排除规则</b>：
 * <ul>
 *   <li>{@code input_type=none} 的工具（番茄钟等纯交互）→ 不是「输入→产出」型，不适合当工具</li>
 *   <li>{@code output_type} 含 image 的工具（抠图/压缩/二维码）→ 产物是文件不是文本，
 *       喂不回上下文；这类工具本来就有独立页面</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolCatalog {

    /** 管线步骤：解析文件（PDF/Word/TXT → 文本） */
    public static final String STEP_PARSE = "parse";
    /** 管线步骤：音频转写 */
    public static final String STEP_TRANSCRIBE = "transcribe";
    /** 管线步骤：图片 OCR */
    public static final String STEP_OCR = "ocr";
    /** 管线步骤：文本加工（走 AI + 该工具的 format/generate 提示词） */
    public static final String STEP_PROCESS = "process";
    /** 管线步骤：图片理解（走多模态模型，把图片真正喂给模型看） */
    public static final String STEP_VISION = "vision";

    /** 内置工具编码：图片理解（不入库，避免出现在工具页） */
    public static final String TOOL_IMAGE_UNDERSTAND = "image-understand";

    private final AiToolMapper aiToolMapper;
    private final AiPromptTemplateService promptTemplateService;
    private final NodeExecutor nodeExecutor;
    private final DocumentParser documentParser;
    private final TranscribeService transcribeService;
    private final OcrService ocrService;
    private final com.example.aitools.service.HistoryService historyService;
    private final com.example.aitools.ai.AiVisionClient aiVisionClient;
    private final com.example.aitools.service.SegmentationService segmentationService;
    private final com.example.aitools.service.FileStorageService fileStorageService;

    /**
     * 智能体可用工具。
     */
    public static final class AgentTool {
        /** 工具 ID（写历史时用） */
        private final Long toolId;
        /** 工具编码 */
        private final String code;
        /** 工具名 */
        private final String name;
        /** 工具说明（来自 DB 的 description，喂给 LLM） */
        private final String description;
        /** 首个输入类型（text / file / audio / image） */
        private final String inputType;
        /** 输出类型 */
        private final String outputType;
        /** 执行管线 */
        private final List<String> pipeline;

        AgentTool(Long toolId, String code, String name, String description,
                  String inputType, String outputType, List<String> pipeline) {
            this.toolId = toolId;
            this.code = code;
            this.name = name;
            this.description = description;
            this.inputType = inputType;
            this.outputType = outputType;
            this.pipeline = pipeline;
        }

        public Long getToolId() {
            return toolId;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getInputType() {
            return inputType;
        }

        public String getOutputType() {
            return outputType;
        }

        public List<String> getPipeline() {
            return pipeline;
        }

        /** 是否需要模型提供「文件」参数 */
        public boolean needsFile() {
            return "file".equals(inputType) || "document".equals(inputType)
                    || "audio".equals(inputType) || "image".equals(inputType);
        }
    }

    /**
     * 列出所有可用的智能体工具。
     */
    public List<AgentTool> listTools() {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getStatus, 1)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .isNotNull(AiTool::getInputType)
                .ne(AiTool::getInputType, "")
                .orderByAsc(AiTool::getSortNo);
        List<AgentTool> tools = new ArrayList<>();
        for (AiTool t : aiToolMapper.selectList(wrapper)) {
            AgentTool tool = toAgentTool(t);
            if (tool != null) {
                tools.add(tool);
            }
        }
        // 内置工具：图片理解（走多模态模型）。不写 DB，避免出现在工具页/工作流里
        tools.add(builtinVisionTool());
        log.info("[agent] 可用工具 {} 个：{}", tools.size(),
                tools.stream().map(AgentTool::getCode).toList());
        return tools;
    }

    /**
     * 按编码取工具；不存在或不可用返回 null。
     */
    public AgentTool find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (TOOL_IMAGE_UNDERSTAND.equals(code.trim())) {
            return builtinVisionTool();
        }
        AiTool t = aiToolMapper.selectOne(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getToolCode, code.trim())
                .eq(AiTool::getStatus, 1)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .last("LIMIT 1"));
        return t == null ? null : toAgentTool(t);
    }

    /**
     * DB 行 → 智能体工具（含管线解析）。
     * <p>返回 null 表示该工具不适合当智能体工具（被排除规则过滤掉）。
     */
    private AgentTool toAgentTool(AiTool t) {
        String inputType = firstInputType(t.getInputType());
        if (inputType == null || "none".equals(inputType)) {
            // 纯交互工具（番茄钟等）不是「输入→产出」型
            return null;
        }
        String outputType = t.getOutputType();
        // 图片产物工具（抠图/证件照换底）现在也开放给智能体：
        // 产物落 COS，文本说明进上下文，URL 通过 [图片] 帧给前端展示
        List<String> pipeline = new ArrayList<>();
        switch (inputType) {
            case "audio":
                pipeline.add(STEP_TRANSCRIBE);
                break;
            case "image":
                pipeline.add(STEP_OCR);
                break;
            case "file":
            case "document":
                pipeline.add(STEP_PARSE);
                break;
            default:
                break;
        }
        if (hasProcessingPrompts(t.getToolCode())) {
            pipeline.add(STEP_PROCESS);
        }
        return new AgentTool(t.getId(), t.getToolCode(), t.getToolName(), t.getDescription(),
                inputType, outputType, pipeline);
    }

    /**
     * 该工具是否有「format + generate」两条系统提示词。
     * <p>
     * <b>两条都要有</b>才算可加工：{@code NodeExecutor#aiText} 缺任一条都会直接报错。
     */
    private boolean hasProcessingPrompts(String toolCode) {
        try {
            return promptTemplateService.getDefaultByUse(toolCode, "format") != null
                    && promptTemplateService.getDefaultByUse(toolCode, "generate") != null;
        } catch (Exception e) {
            log.warn("[agent] 查询工具提示词失败 tool={}", toolCode, e);
            return false;
        }
    }

    /**
     * 执行工具（并写一条工具历史，来源 = 智能体）。
     *
     * @param tool   工具
     * @param input  输入：文本类工具传文本；文件/音频/图片类工具传**文件标识**
     *               （本地路径 / data URL / http URL，由 {@code NodeExecutor#toMultipartFile} 统一处理）
     * @param params 节点参数（初版传空，提示词走工具的系统默认）
     * @param userId 发起用户（写历史用；null 则不写历史）
     * @param runId  本次智能体运行 ID（写历史用，可为 null）
     * @return 工具输出（文本）
     */
    public ToolResult invoke(AgentTool tool, String input, Map<String, String> params,
                             Long userId, String runId) {
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("工具输入为空");
        }
        Long historyId = null;
        if (userId != null && tool.getToolId() != null) {
            historyId = historyService.createAgentToolHistory(userId, tool.getToolId(), null,
                    tool.getCode(), brief(input), runId, tool.getName());
        }
        long start = System.currentTimeMillis();
        try {
            // 图片产物工具：走独立管线（分割 → 落 COS），返回带 URL 的结果
            if (isImageOutput(tool)) {
                ToolResult imageResult = runImageTool(tool, input, params);
                if (historyId != null) {
                    historyService.completeHistory(historyId, imageResult.getText(),
                            (int) (System.currentTimeMillis() - start));
                }
                return imageResult;
            }
            String content = input;
            for (String step : tool.getPipeline()) {
                content = runStep(tool, step, content, params);
            }
            if (historyId != null) {
                historyService.completeHistory(historyId, content,
                        (int) (System.currentTimeMillis() - start));
            }
            return ToolResult.text(content);
        } catch (Exception e) {
            if (historyId != null) {
                try {
                    historyService.failHistory(historyId, e.getMessage());
                } catch (Exception ignore) {
                    // 历史写失败不影响工具本身的错误抛出
                }
            }
            throw e;
        }
    }

    /** 写历史时的输入摘要（长文本只留头，避免历史表膨胀） */
    private String brief(String input) {
        if (input == null) {
            return null;
        }
        String oneLine = input.replace("\r", " ").replace("\n", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
    }

    private String runStep(AgentTool tool, String step, String content, Map<String, String> params) {
        switch (step) {
            case STEP_PARSE:
                return documentParser.parse(nodeExecutor.toMultipartFile(content, "input"));
            case STEP_TRANSCRIBE:
                return transcribeService.transcribe(
                        nodeExecutor.toMultipartFile(content, "audio.mp3"), null).getText();
            case STEP_OCR:
                return ocrService.recognizeText(nodeExecutor.toMultipartFile(content, "input.png"));
            case STEP_PROCESS:
                return nodeExecutor.processText(tool.getCode(), content, params);
            case STEP_VISION: {
                String question = params == null ? null : params.get("question");
                if (question == null || question.isBlank()) {
                    question = "请详细描述这张图片的内容；如果有人物，说明其外貌、动作与所处场景。用中文回答。";
                }
                try {
                    byte[] bytes = nodeExecutor.toMultipartFile(content, "image.png").getBytes();
                    return aiVisionClient.chatImages(question, List.of(bytes), null);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("读取图片失败：" + e.getMessage(), e);
                }
            }
            default:
                throw new IllegalStateException("未知的工具管线步骤：" + step);
        }
    }

    /** 是否为图片产物工具 */
    private boolean isImageOutput(AgentTool tool) {
        return tool.getOutputType() != null && tool.getOutputType().contains("image");
    }

    /**
     * 图片产物工具：原图 → 分割 → 结果落 COS。
     * <p>
     * 证件照换底色按 params.backgroundColor（red/blue/white）；其余默认去背景（透明）。
     */
    private ToolResult runImageTool(AgentTool tool, String input, Map<String, String> params) {
        try {
            byte[] src = nodeExecutor.toMultipartFile(input, "input.png").getBytes();
            String color = params == null ? null : params.get("backgroundColor");
            com.example.aitools.dto.SegmentResult result;
            if ("id-photo-bg-change".equals(tool.getCode())) {
                result = segmentationService.removeBackground(src, color == null ? "white" : color);
            } else {
                result = segmentationService.removeBackgroundTransparent(src);
            }
            com.example.aitools.dto.FileUploadResponse up = fileStorageService.store(
                    result.getImageBytes(), "result.png", "image/png", "ai-bg");
            String url = up.getFileUrl();
            return ToolResult.image(
                    "图片处理完成，已生成结果图，可直接下载：\n" + url, List.of(url));
        } catch (Exception e) {
            throw new IllegalStateException("图片处理失败：" + e.getMessage(), e);
        }
    }

    /** 内置的图片理解工具（不入库；find 与 listTools 共用） */
    private AgentTool builtinVisionTool() {
        return new AgentTool(null, TOOL_IMAGE_UNDERSTAND, "图片理解",
                "看图回答问题：人物、场景、物体、画面文字、图表内容等；不识别具体身份",
                "image", "text", List.of(STEP_VISION));
    }

    /** 取逗号分隔类型里的第一个（与 NodeExecutor#firstInputType 同口径） */
    private String firstInputType(String inputTypes) {
        if (inputTypes == null || inputTypes.isBlank()) {
            return null;
        }
        return Arrays.stream(inputTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
