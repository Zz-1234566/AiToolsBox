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

    /** 支持的存储前缀（目录）：头像 / AI 图片 / 去背景 / 通用用户文件区，空串表示根目录 */
    public static final java.util.Set<String> ALLOWED_FILE_PREFIXES =
            java.util.Set.of("", "avatar", "ai-image", "ai-bg", "file");

    /** 通用用户文件区前缀（强制登录后拼 userId 子目录） */
    public static final String FILE_PREFIX_USER_FILE = "file";

    /** 本地存储静态资源访问前缀 */
    public static final String LOCAL_STATIC_PATH_PREFIX = "/uploads/";

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
