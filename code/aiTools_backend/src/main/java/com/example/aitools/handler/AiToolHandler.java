package com.example.aitools.handler;

import com.example.aitools.ai.AiClient;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchProcessResult;
import com.example.aitools.entity.BatchTask;
import com.example.aitools.utils.SpringContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Consumer;

/**
 * 工具处理方式抽象：横向 = 单文件/多文件，纵向 = SSE长文本/JSON结构化
 * <p>
 * 单一入口拆成 4 个方法，按需重写：
 * <ul>
 *     <li>{@link #handleSingleText}     单文件 → SSE 长文本（现有 5 个工具）</li>
 *     <li>{@link #handleSingleJson}     单文件 → JSON 结构化（meeting-minutes 试点）</li>
 *     <li>{@link #handleBatchText}      多文件 → SSE 长文本（ai-file-reader 等）</li>
 *     <li>{@link #handleBatchJson}      多文件 → JSON 结构化（未来）</li>
 * </ul>
 * <p>
 * Bean 注册名约定（Factory 按 name 取）：
 * <ul>
 *     <li>sse-text-single</li>
 *     <li>json-single</li>
 *     <li>sse-text-batch</li>
 *     <li>json-batch</li>
 * </ul>
 */
public interface AiToolHandler {

    /**
     * 路由决策：让 AI 判断当前请求走 sse 长文本还是 json 结构化。
     * <p>
     * 决策顺序：
     * <ol>
     *     <li>AI 决策（复用 ai.minimax-text.* 模型）：用严格 JSON 指令约束输出；解析失败则进入 2</li>
     *     <li>关键词匹配兜底：含 json/结构化/表格 → json，含 markdown/长文本 → sse</li>
     *     <li>默认返回 sse（会议纪要99% 是叙述，反业务直觉默认 json 已被历史踩过坑）</li>
     * </ol>
     *
     * @return "sse" 或 "json"
     */
    default String decideRoute(String toolCode, String content, String promptFormat, String promptGenerate, String userRequirement) {
        // interface 没有 @Slf4j（Lombok 不支持 interface 注解），手动拿 logger
        Logger log = LoggerFactory.getLogger(AiToolHandler.class);
        // 1) 先走 AI 决策：用 JSON 输出约束提高命中率
        try {
            AiClient ai = SpringContextHolder.getBean(AiClient.class);
            // 强约束 prompt：限定输出结构是 JSON 对象，route 字段只能二选一
            String routeSystemPrompt =
                    "你是路由决策助手。判断用户当前请求更适合哪种输出形态。\n" +
                    "规则：\n" +
                    "- 选 \"json\"：用户明确要结构化字段、表格、列表、固定 schema、机器可解析数据\n" +
                    "- 选 \"sse\"：用户要长文本、叙述、说明、报告、Markdown 排版的会议纪要\n" +
                    "【强约束】必须仅输出一个 JSON 对象，结构如下（不要任何其他字符、Markdown、解释、前缀）：\n" +
                    "{\"route\":\"json\"} 或 {\"route\":\"sse\"}\n" +
                    "绝对不要输出 sse/json 之外的内容。如果拿不准就选 sse。";
            String routeUserPrompt =
                    "工具：" + toolCode + "\n" +
                    "用户原文：\n" + (content == null ? "" : content) + "\n\n" +
                    "用户填写的需求：\n" + (userRequirement == null ? "" : userRequirement) + "\n\n" +
                    "格式提示词（promptFormat）：\n" + (promptFormat == null ? "" : promptFormat) + "\n\n" +
                    "生成提示词（promptGenerate）：\n" + (promptGenerate == null ? "" : promptGenerate);
            String aiAnswer = ai.chat(routeSystemPrompt, routeUserPrompt);
            String trimmed = aiAnswer == null ? "" : aiAnswer.trim().toLowerCase();
            // 严格匹配：包含 route 字段且 value 是 json/sse
            if (trimmed.contains("\"route\":\"json\"") || trimmed.contains("'route':'json'")) {
                return "json";
            }
            if (trimmed.contains("\"route\":\"sse\"") || trimmed.contains("'route':'sse'")) {
                return "sse";
            }
            // 宽松匹配（容忍 AI 偶尔不规范输出，比如直接返回 "json"）
            if (trimmed.contains("json") && !trimmed.contains("sse")) {
                log.warn("[decideRoute] AI 返回不规范但含 json 字样: {}", aiAnswer);
                return "json";
            }
            if (trimmed.contains("sse") && !trimmed.contains("json")) {
                log.warn("[decideRoute] AI 返回不规范但含 sse 字样: {}", aiAnswer);
                return "sse";
            }
            log.warn("[decideRoute] AI 返回无法解析: {}", aiAnswer);
        } catch (Throwable ignore) {
            // AI 调用异常 / 返回非 sse/json / Spring 上下文未就绪 → 进入 2 兜底
            log.warn("[decideRoute] AI 决策异常，进入关键词兜底", ignore);
        }

        // 2) 关键词匹配兜底（同时看 userRequirement + content + promptFormat + promptGenerate）
        String combined = ((userRequirement == null ? "" : userRequirement) + "\n"
                + (content == null ? "" : content) + "\n"
                + (promptFormat == null ? "" : promptFormat) + "\n"
                + (promptGenerate == null ? "" : promptGenerate)).toLowerCase();
        if (combined.contains("结构化") || combined.contains("表格形式") || combined.contains("列表形式")) {
            return "json";
        }
        // 注意：combined.contains("json") 不再作为判定条件（提示词里常出现"json"会误判）
        if (combined.contains("markdown") || combined.contains("长文本") || combined.contains("详细说明") || combined.contains("完整叙述")) {
            return "sse";
        }

        // 3) 默认 sse：会议纪要绝大多数场景是叙述；json 用户没明确要就给 sse 更符合预期
        return "sse";
    }

    /**
     * 单文件 → SSE 长文本流式输出
     *
     * @param toolCode       工具编码
     * @param userId         用户 ID
     * @param content        输入文本（已经过 OCR/文档解析等预处理）
     * @param promptFormat   format 类系统提示词（可空，走默认）
     * @param promptGenerate generate 类系统提示词（可空，走默认）
     * @param promptId       用户选中的提示词 ID（可空）
     * @param onChunk        流式每块回调
     * @return 累积完整文本
     */
    String handleSingleText(String toolCode, Long userId, String content,
                            String promptFormat, String promptGenerate, Long promptId,
                            Consumer<String> onChunk);

    /**
     * 单文件 → JSON 结构化输出
     *
     * @param toolCode       工具编码
     * @param userId         用户 ID
     * @param content        输入文本
     * @param promptFormat   format 类系统提示词
     * @param promptGenerate generate 类系统提示词
     * @param promptId       用户选中的提示词 ID（可空）
     * @return 解析后的 JSON 字符串（合法 JSON，可直接序列化给前端）
     */
    String handleSingleJson(String toolCode, Long userId, String content,
                            String promptFormat, String promptGenerate, Long promptId);

    /**
     * 多文件 → SSE 长文本（异步跑批 + 增量入库）
     */
    BatchProcessResult handleBatchText(String toolCode, Long userId, List<BatchFilePayload> files,
                                       String promptFormat, String promptGenerate, Long promptId,
                                       String batchId);

    /**
     * 多文件 → JSON 结构化（异步跑批 + 增量入库）
     */
    BatchProcessResult handleBatchJson(String toolCode, Long userId, List<BatchFilePayload> files,
                                       String promptFormat, String promptGenerate, Long promptId,
                                       String batchId);
}