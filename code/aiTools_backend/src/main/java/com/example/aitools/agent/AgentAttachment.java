package com.example.aitools.agent;

import lombok.Data;

/**
 * 智能体的一次运行附件。
 *
 * <p><b>为什么要「稳定 ID」而不是把路径给模型</b>：
 * 模型不知道服务器本地路径，也不该知道（COS 签名 URL 还带时效）。
 * 所以给模型的是 {@code file_1} / {@code file_2} 这种**本次运行内稳定**的 ID，
 * 服务端维护 ID → 真实文件标识的映射，模型只管引用 ID。
 */
@Data
public class AgentAttachment {

    /** 本次运行内的稳定 ID（file_1 / file_2…），模型用它引用附件 */
    private String id;

    /** 原始文件名（给模型看，便于它判断这是会议录音还是文档） */
    private String fileName;

    /** 文件标识：本地路径 / data URL / http URL（服务端用，不给模型） */
    private String fileRef;

    /** 字节数 */
    private Long size;

    public AgentAttachment() {
    }

    public AgentAttachment(String id, String fileName, String fileRef, Long size) {
        this.id = id;
        this.fileName = fileName;
        this.fileRef = fileRef;
        this.size = size;
    }
}
