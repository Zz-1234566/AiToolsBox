package com.example.aitools.common;

public class Constants {

    private Constants() {}

    /** Token header name */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token prefix */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** request attribute key：JwtAuthFilter 写入当前用户 ID，AuthUtil 从这里读 */
    public static final String REQ_ATTR_USER_ID = "aitoolbox.currentUserId";

    /** Account prefix */
    public static final String ACCOUNT_PREFIX = "AIT";

    /** Account random digit count */
    public static final int ACCOUNT_DIGIT_COUNT = 8;

    /** User status: normal */
    public static final int USER_STATUS_NORMAL = 1;

    /** User status: disabled */
    public static final int USER_STATUS_DISABLED = 0;

    // ==================== 用户角色（sys_user.role） ====================

    /** 角色：管理员（可管理系统预制的系统提示词） */
    public static final String ROLE_ADMIN = "admin";

    /** 角色：普通用户（只能使用系统预制提示词，不可增删改） */
    public static final String ROLE_USER = "user";

    /** Logical delete: normal */
    public static final int DR_NORMAL = 0;

    /** Logical delete: deleted */
    public static final int DR_DELETED = 1;

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** Max page size */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 批量任务（B2 多文件上传） ====================

    // ==================== 鉴权路径白名单（P0-A2 终极修复）====================
    // 不在白名单内的所有路径都要求登录。AuthInterceptor 强制拦截。
    // 新加 Controller 端点**不需要在 Controller 写任何鉴权代码**，只要不在白名单就自动鉴权。
    // 注意：白名单里的路径是真正公开的（不需要登录也能访问）

    /** 公开路径前缀（不需要登录）；精确匹配（不是 startsWith） */
    public static final java.util.Set<String> PUBLIC_PATH_PATTERNS = java.util.Set.of(
            // 用户注册 / 登录 / 找回密码
            "/api/user/login",
            "/api/user/register",
            "/api/user/find-account",
            "/api/user/reset-password",
            // 邮件验证码
            "/api/mail/send-code",
            // 系统提示词（公开给所有用户看默认 prompt）
            "/api/prompt/system/list",
            "/api/prompt/system/format"
    );

    /** 单次批量最多文件数（与文件末尾的 BATCH_MAX_FILE_COUNT 重复，本处 P0 移除，避免编译错误） */

    /** 单文件大小上限（字节）：20MB（与 spring.servlet.multipart.max-file-size 保持一致） */
    public static final long BATCH_SINGLE_FILE_MAX_SIZE = 20L * 1024 * 1024;

    // ==================== 通用文件上传（/api/file/upload） ====================

    /** 单文件大小上限：20MB */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** 支持的文件扩展名（图片 + 文档 + 音频） */
    public static final java.util.Set<String> ALLOWED_FILE_EXTENSIONS =
            java.util.Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt",
                    "mp3", "wav", "m4a", "aac", "flac", "ogg", "amr");

    /** 支持的存储前缀（目录）：头像 / AI 图片 / 去背景 / 通用用户文件区 / 工具产物，空串表示根目录 */
    public static final java.util.Set<String> ALLOWED_FILE_PREFIXES =
            java.util.Set.of("", "avatar", "ai-image", "ai-bg", "file", "tool-output");

    /** 通用用户文件区前缀（强制登录后拼 userId 子目录） */
    public static final String FILE_PREFIX_USER_FILE = "file";

    /** 工具产物存储前缀（全局工具产物文件，与用户上传区隔离，便于生命周期管理） */
    public static final String TOOL_OUTPUT_PREFIX = "tool-output";

    /** 头像存储前缀（用户头像，COS 下公开读，前端 <image src> 直接引用） */
    public static final String AVATAR_PREFIX = "avatar";

    /** AI 生成图片存储前缀（COS 下公开读） */
    public static final String AI_IMAGE_PREFIX = "ai-image";

    /** AI 去背景图片存储前缀（COS 下公开读） */
    public static final String AI_BG_PREFIX = "ai-bg";

    /** 本地存储静态资源访问前缀 */
    public static final String LOCAL_STATIC_PATH_PREFIX = "/uploads/";

    /**
     * 本地存储模式下无需登录即可访问的静态资源前缀（每项均已含 {@link #LOCAL_STATIC_PATH_PREFIX}）。
     * <p>
     * 收录依据 = {@code FileStorageService#isPrivatePrefix} 的反面：这些前缀在 COS 实现里
     * 会走 {@code setObjectAcl(PublicRead)}，拿到直链即可访问（工具产物语义是「链接可分享」，
     * 头像 / AI 图则是前端 {@code <image src>} 直接引用，浏览器不会带 Authorization 头）。
     * 本地存储把它们落在同一个 /uploads/ 下，语义必须一致，否则本地模式一律 401、
     * 前端出现破图，两种存储模式行为不一致。
     * <p>
     * 刻意<b>不</b>放行 {@code /uploads/file/}：用户上传的原始文件是私有的，
     * COS 下靠签名 URL 访问，本地模式保持要求登录态。
     * <p>
     * ⚠️ 必须配合 AuthInterceptor 对<b>归一化后</b>的路径做前缀匹配使用：
     * 拦截器按原始 requestURI 匹配、静态映射按归一化后的真实路径取文件，
     * 两者时机不同，只有归一化后再判断才不会被 {@code ../} 穿越绕过。
     * ⚠️ 每项必须以 {@code /} 结尾，否则 {@code startsWith} 会误伤
     * {@code /uploads/avatarxxx} 这类同前缀的其它目录。
     */
    public static final java.util.Set<String> PUBLIC_LOCAL_PATH_PREFIXES = java.util.Set.of(
            LOCAL_STATIC_PATH_PREFIX + TOOL_OUTPUT_PREFIX + "/",
            LOCAL_STATIC_PATH_PREFIX + AVATAR_PREFIX + "/",
            LOCAL_STATIC_PATH_PREFIX + AI_IMAGE_PREFIX + "/",
            LOCAL_STATIC_PATH_PREFIX + AI_BG_PREFIX + "/"
    );

    // ==================== AI 内容长度（喂模型 / 落库） ====================

    /**
     * 喂给大模型的输入文本上限（字符）。
     * <p>
     * 与 {@code DocumentParser.MAX_TEXT_LENGTH} 同值，此处作为全局常量，
     * 供其他需要限制 prompt 长度的场景复用。
     */
    public static final int AI_INPUT_MAX_LENGTH = 20000;

    /**
     * AI 输出落库上限（<b>UTF-16 字符数</b>，非 codepoint 数）。
     * <p>
     * MySQL {@code text} 上限 65535 <b>字节</b>；UTF-8 中文 1 字符 = 3 字节、
     * emoji = 4 字节，故按最坏情况（4 字节/字符）取 16000 留足余量，
     * 避免超长输出写库时被截断或报错。
     * <p>
     * 截断实现见 {@code HistoryServiceImpl#truncate}，用
     * {@code offsetByCodePoints} 保证不从代理对中间切开。
     */
    public static final int AI_OUTPUT_MAX_LENGTH = 16000;

    /** 截断后追加的省略标记 */
    public static final String TRUNCATE_SUFFIX = "\n\n【内容过长，已截断】";

    // ==================== 邮箱验证码（/api/mail/send-code） ====================

    /** 验证码场景：注册 */
    public static final String CODE_TYPE_REGISTER = "register";

    /** 验证码场景：重置密码 */
    public static final String CODE_TYPE_RESET_PASSWORD = "reset-password";

    /** 验证码场景白名单（Controller / Service 校验共用） */
    public static final java.util.Set<String> CODE_TYPE_ALLOWED =
            java.util.Set.of(CODE_TYPE_REGISTER, CODE_TYPE_RESET_PASSWORD);

    // ==================== 验证码失败锁定 ====================

    /** 单 target 连续失败上限（达到后锁 10 分钟，期间不再校验验证码） */
    public static final int VERIFY_CODE_MAX_FAIL_COUNT = 5;

    /** 失败计数与锁定时长（分钟） */
    public static final int VERIFY_CODE_FAIL_LOCK_MINUTES = 10;

    // ==================== 历史记录（/api/history） ====================

    /** 历史状态：处理中 */
    public static final int HISTORY_STATUS_PROCESSING = 0;

    /** 历史状态：成功 */
    public static final int HISTORY_STATUS_SUCCESS = 1;

    /** 历史状态：失败 */
    public static final int HISTORY_STATUS_FAILED = 2;

    /** 历史来源：单工具直接调用（runId / nodeId 为空） */
    public static final int HISTORY_SOURCE_SINGLE = 1;

    /** 历史来源：工作流节点调用（带 runId / nodeId / nodeName） */
    public static final int HISTORY_SOURCE_WORKFLOW = 2;

    /** 默认历史记录查询条数 */
    public static final int HISTORY_LIST_DEFAULT_LIMIT = 10;

    // ==================== SSE 流式 / 批量任务 ====================

    /** SSE emitter 超时（毫秒）：2 分钟。流式 AI 调用超过此时间视为超时 */
    public static final long SSE_TIMEOUT_MS = 120_000L;

    /** 单次批量上传文件数上限 */
    public static final int BATCH_MAX_FILE_COUNT = 10;

    /** 单次批量上传总大小上限（字节）：200MB */
    public static final long BATCH_MAX_TOTAL_SIZE = 200L * 1024 * 1024;

    // ==================== 工作流 ====================

    /** 工作流最大层数（拓扑分层后 level 不得超过此值） */
    public static final int WORKFLOW_MAX_DEPTH = 5;

    /** 工作流同层节点并行执行上限 */
    public static final int WORKFLOW_MAX_PARALLEL = 4;

    /** 工作流名称最大长度 */
    public static final int WORKFLOW_NAME_MAX_LENGTH = 64;
}
