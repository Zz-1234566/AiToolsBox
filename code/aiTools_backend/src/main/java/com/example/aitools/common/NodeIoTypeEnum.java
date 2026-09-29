package com.example.aitools.common;

/**
 * 节点输入/输出类型枚举（与 sys_aitools_tool.input_type / output_type 逗号分隔值 1:1）。
 * <p>
 * 类型匹配规则：upstream.outputType ⊆ downstream.inputTypes
 * （上游每一种输出，下游都必须能接住）
 * <ul>
 *   <li>{@code none}     无输入/无输出（番茄钟等纯交互工具）</li>
 *   <li>{@code text}     文本</li>
 *   <li>{@code audio}    音频文件</li>
 *   <li>{@code document} 文档（pdf / word / txt）</li>
 *   <li>{@code image}    图片</li>
 *   <li>{@code file}     任意文件（放宽匹配，如文档重点提取）</li>
 * </ul>
 */
public enum NodeIoTypeEnum {

    NONE("none"),
    TEXT("text"),
    AUDIO("audio"),
    DOCUMENT("document"),
    IMAGE("image"),
    FILE("file");

    private final String code;

    NodeIoTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 按 code 解析，未知返回 null
     */
    public static NodeIoTypeEnum of(String code) {
        if (code == null) return null;
        for (NodeIoTypeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code.trim())) return e;
        }
        return null;
    }

    /**
     * 上游某一种输出类型，能否被下游的输入类型集合接住。
     * <p>
     * 规则：
     * <ul>
     *   <li>下游含 {@code file} → 可接任意类型（file 表示"任意文件"，如文档重点提取）</li>
     *   <li>否则要求上游 code 精确出现在下游集合中</li>
     * </ul>
     *
     * @param upstreamOutput  上游输出类型（单个 code，如 "text"）
     * @param downstreamInputs 下游输入类型集合（逗号分隔，如 "text,file"）
     */
    public static boolean canConnect(String upstreamOutput, String downstreamInputs) {
        if (upstreamOutput == null || upstreamOutput.isBlank()) return false;
        if (downstreamInputs == null || downstreamInputs.isBlank()) return false;
        String out = upstreamOutput.trim().toLowerCase();
        for (String in : downstreamInputs.split(",")) {
            String t = in.trim().toLowerCase();
            if (t.isEmpty()) continue;
            // 下游声明 file = 任意文件 → 放行
            if (FILE.code.equals(t)) return true;
            if (t.equals(out)) return true;
        }
        return false;
    }
}
