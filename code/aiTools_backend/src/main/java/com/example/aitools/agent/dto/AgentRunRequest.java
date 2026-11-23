package com.example.aitools.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 智能体运行请求（JSON body）。
 *
 * <p><b>为什么附件走「先上传拿 URL」而不是 multipart 直传</b>：
 * SSE 流式请求在前端是「JSON POST + 增量读响应」，
 * multipart 直传会让 App 端的跨层 SSE 桥接没法带文件。
 * 所以附件先用现成的 {@code POST /api/file/upload} 上传拿 URL，
 * 这里只传 URL —— 工具执行时由 {@code NodeExecutor#toMultipartFile} 统一下载。
 */
@Data
public class AgentRunRequest {

    /** 会话ID（为空则新建一个会话） */
    private String sessionId;

    /** 用户目标 */
    private String goal;

    /** 用户选定的模型编码（BYOK）；为空走默认凭据 */
    private String modelCode;

    /** 附件（可为空） */
    private List<Attachment> files;

    /**
     * 一个附件。
     */
    @Data
    public static class Attachment {

        /** 原始文件名（给模型看） */
        private String name;

        /** 文件地址（COS / 本地） */
        private String url;
    }
}
