package com.example.aitools.common;

/**
 * 工具编码常量（sys_aitools_tool.tool_code）。
 *
 * <p><b>为什么单独成类</b>：工具编码是**跨模块的业务主键** ——
 * Controller 建批量任务、Handler 按能力分派、Service 写提示词历史，三处都要用它。
 * 散落成字符串字面量时，改一个编码要全局搜索替换，漏一处就是「工具跑不起来但编译通过」，
 * 且没有任何提示能指向它。
 *
 * <p><b>与前端 tools.js 的关系</b>：本组常量必须与前端
 * {@code code/aiTools_frontend/src/config/tools.js} 的键**逐字一致**。
 * 新增 / 下线工具时，两处 + {@code sql/init.sql} 的工具表三处同步改
 * （详见 AGENTS.md §5）。
 *
 * <p><b>这些都是编译期常量</b>（{@code static final String} + 字面量字面值），
 * 因此可以直接用作 {@code switch} 的 case 标签 —— 改造时把
 * {@code case "xxx"} 换成 {@code case ToolCodes.XXX} 不影响 switch 语义。
 */
public final class ToolCodes {

    private ToolCodes() {
        // 常量类，禁止实例化
    }

    // ==================== 加工层（走 AI 文本加工） ====================

    /** 工作总结：把零散记录整理成结构化总结 */
    public static final String WORK_SUMMARY = "work-summary";

    /** 文档重点提取：从已有文本提炼核心要点 */
    public static final String DOC_KEYPOINT_EXTRACT = "doc-keypoint-extract";

    /** 周报生成：按输入生成周报 */
    public static final String WEEKLY_REPORT = "weekly-report";

    /** 会议纪要：把会议内容整理成纪要 */
    public static final String MEETING_MINUTES = "meeting-minutes";

    // ==================== 解析层（不调 AI） ====================

    /** 文档转文本：提取 PDF / Word / TXT / 图片 / 扫描件中的文字 */
    public static final String DOC_TO_TEXT = "doc-to-text";

    /** 录音转写：语音转文字 */
    public static final String AUDIO_TRANSCRIBE = "audio-transcribe";

    // ==================== 图片创意层 ====================

    /** 图片背景去除：去背景 / 换底色 / 换背景图（本地 ONNX 推理） */
    public static final String IMAGE_BG_REMOVER = "image-bg-remover";

    // ==================== 历史能力（已下线，保留常量以便识别旧数据） ====================

    /**
     * 智能识别（OCR 工具族）。
     * <p>已由 doc-to-text 能力覆盖并下线（dr=1），此处保留常量用于**识别历史数据**。
     */
    public static final String OCR_RECOGNIZE = "ocr-recognize";

    /**
     * AI 文件解读（多模态）。
     * <p>与「加工层只吃文本」的定位冲突，已下线（dr=1），此处保留常量用于识别历史数据。
     */
    public static final String AI_FILE_READER = "ai-file-reader";
}
