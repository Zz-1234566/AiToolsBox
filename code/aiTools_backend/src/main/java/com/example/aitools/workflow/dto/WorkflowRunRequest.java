package com.example.aitools.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 运行工作流请求。
 */
@Data
public class WorkflowRunRequest {

    /**
     * 运行时输入：nodeId → 输入数组。
     * <ul>
     *   <li>文本类源节点：数组元素为文本内容</li>
     *   <li>文件类源节点：数组元素为文件路径 或 data URL（H5 端）</li>
     * </ul>
     * 仅源节点（deps 为空）需要提供；有上游的节点忽略此项。
     */
    private Map<String, List<String>> inputs;

    /**
     * 源节点输入文件的原始文件名，nodeId → 文件名数组。与 {@link #inputs} 并行传递、按数组下标一一对应。
     * <p>
     * <b>为什么必须走旁路字段、不能把 inputs 改成对象数组：</b>
     * <ul>
     *   <li>inputs 是强类型 {@code Map<String, List<String>>}，前端若传对象，
     *       Jackson 反序列化直接抛 MismatchedInputException → HTTP 400，整个运行功能废掉；</li>
     *   <li>即便绕过反序列化，NodeExecutor#toMultipart 靠 {@code startsWith("http")}
     *       判断 URL、否则当本地路径 {@code new File(对象)} 也必然失败。</li>
     * </ul>
     * 所以 inputs 保持「只传 URL」，原始名走本字段单独传。前端不上传时为 null（老格式兼容）。
     */
    private Map<String, List<String>> inputFileNames;
}
