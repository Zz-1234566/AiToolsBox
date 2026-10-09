package com.example.aitools.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码。
 * <p>
 * <b>编码规则（5 位）</b>：首位数字代表业务模块，后 4 位为模块内序号。
 * <pre>
 *   1xxxx  认证 / 用户      登录、注册、JWT、权限、账号
 *   2xxxx  AI 办公工具      工作总结、周报、会议纪要、文档重点提取
 *   3xxxx  图片创意工具      证件照换背景、人像换背景、图片压缩、二维码
 *   4xxxx  OCR 识别         智能识别、票据识别
 *   5xxxx  文档处理         文档转文本、文档解析
 *   6xxxx  音频处理         录音转写
 *   7xxxx  工作流           编排、节点、运行
 *   8xxxx  文件 / 存储      上传、COS、本地存储
 *   9xxxx  参数 / 系统      通用参数校验、系统内部错误
 * </pre>
 * <b>通用语义码</b>（HTTP 语义，前端依赖，勿改）：{@code 200} 成功、{@code 401} 未登录、
 * {@code 403} 无权限、{@code 404} 资源不存在、{@code 500} 未分类失败。
 * <p>
 * <b>新增错误码</b>：按模块选首位数字，在该模块段内递增，并在下方对应分组内添加，
 * 避免跨模块撞号。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ==================== 通用语义码（HTTP 语义）====================
    SUCCESS(200, "操作成功"),
    /** 未分类失败（历史遗留默认码，新代码请使用具体业务码） */
    FAIL(500, "操作失败"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),

    // ==================== 1xxxx 认证 / 用户 ====================
    USER_NOT_FOUND(10001, "用户不存在"),
    PASSWORD_ERROR(10002, "密码错误"),
    USER_DISABLED(10003, "用户已被禁用"),
    USER_ALREADY_EXISTS(10004, "该账号已被注册"),
    EMAIL_ALREADY_EXISTS(10005, "该邮箱已被使用"),
    PASSWORD_MISMATCH(10006, "两次密码不一致"),
    TOKEN_EXPIRED(10007, "登录已过期，请重新登录"),
    ACCOUNT_NOT_FOUND(10008, "账号不存在"),

    // ==================== 2xxxx AI 办公工具 ====================
    AI_TOOL_FAILED(20001, "AI 处理失败，请稍后重试"),
    AI_SERVICE_ERROR(20002, "AI 服务暂时不可用，请稍后重试"),
    AI_EMPTY_RESULT(20003, "AI 未返回内容，请重试"),
    PROMPT_INVALID(20004, "提示词无效或已被删除，请重新选择"),
    PROMPT_NAME_DUPLICATE(20005, "该工具下已存在同名提示词"),

    // ==================== 3xxxx 图片创意工具 ====================
    IMAGE_TOOL_FAILED(30001, "图片处理失败，请稍后重试"),
    IMAGE_UNSUPPORTED(30002, "仅支持图片格式"),
    /** 分割模型不可用：未启用 / 模型文件缺失 / 加载失败 */
    SEGMENT_MODEL_UNAVAILABLE(30003, "去背景能力暂不可用，请稍后重试"),
    /** 推理失败：ONNX Runtime 异常等，前端只看到通用文案，根因进日志 */
    SEGMENT_INFER_FAILED(30004, "人像识别失败，请换一张正面清晰的证件照重试"),
    /** 底色参数非法 */
    SEGMENT_BAD_COLOR(30005, "底色参数不合法"),
    /** 图片像素数超过上限（超大图直接拒，避免 OOM） */
    IMAGE_TOO_LARGE(30006, "图片分辨率过大，请上传 4000 万像素以内的图片"),

    // ==================== 4xxxx OCR 识别 ====================
    OCR_FAILED(40001, "识别失败，请稍后重试"),
    OCR_UNSUPPORTED(40002, "仅支持图片格式（PNG/JPG/JPEG/BMP）"),

    // ==================== 5xxxx 文档处理 ====================
    DOC_PARSE_FAILED(50001, "文档解析失败，请检查文件后重试"),
    DOC_UNSUPPORTED(50002, "暂不支持该文件类型"),
    DOC_EMPTY(50003, "文档内容为空，无法处理"),

    // ==================== 6xxxx 音频处理 ====================
    AUDIO_FAILED(60001, "录音处理失败，请稍后重试"),
    AUDIO_UNSUPPORTED(60002, "暂不支持该音频格式"),

    // ==================== 7xxxx 工作流 ====================
    WORKFLOW_INVALID(70001, "工作流配置有误，请检查节点设置"),
    WORKFLOW_RUN_FAILED(70002, "工作流运行失败"),
    WORKFLOW_DEPTH_EXCEEDED(70003, "工作流层数超过上限"),
    WORKFLOW_CYCLE(70004, "工作流存在循环依赖"),
    WORKFLOW_TYPE_MISMATCH(70005, "节点连线类型不匹配"),
    WORKFLOW_NODE_FAILED(70006, "节点执行失败"),

    // ==================== 8xxxx 文件 / 存储 ====================
    FILE_UNSUPPORTED(80001, "不支持的文件类型"),
    FILE_TOO_LARGE(80002, "文件大小超出限制"),
    FILE_UPLOAD_FAILED(80003, "文件上传失败，请重试"),
    FILE_NOT_FOUND(80004, "文件不存在或已失效"),

    // ==================== 9xxxx 参数 / 系统 ====================
    PARAM_ERROR(90001, "参数错误"),
    PARAM_MISSING(90002, "缺少必要参数"),
    CODE_ERROR(90003, "验证码错误或已过期"),
    CODE_TOO_FREQUENT(90004, "发送太频繁，请稍后再试"),
    SYSTEM_ERROR(90005, "系统内部错误"),
    SERVICE_UNAVAILABLE(90006, "服务不可用，请稍后重试"),
    OPERATION_NOT_ALLOWED(90007, "当前操作不被允许");

    private final int code;
    private final String message;
}
