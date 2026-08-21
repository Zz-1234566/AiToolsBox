package com.example.aitools.common;

public class Constants {

    private Constants() {}

    /** Token header name */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token prefix */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Account prefix */
    public static final String ACCOUNT_PREFIX = "AIT";

    /** Account random digit count */
    public static final int ACCOUNT_DIGIT_COUNT = 8;

    /** User status: normal */
    public static final int USER_STATUS_NORMAL = 1;

    /** User status: disabled */
    public static final int USER_STATUS_DISABLED = 0;

    /** Logical delete: normal */
    public static final int DR_NORMAL = 0;

    /** Logical delete: deleted */
    public static final int DR_DELETED = 1;

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** Max page size */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 批量任务（B2 多文件上传） ====================

    /** 单次批量最多文件数 */
    public static final int BATCH_MAX_FILE_COUNT = 10;

    /** 批量文件总大小上限（字节）：200MB */
    public static final long BATCH_MAX_TOTAL_SIZE = 200L * 1024 * 1024;

    /** 单文件大小上限（字节）：20MB（与 spring.servlet.multipart.max-file-size 保持一致） */
    public static final long BATCH_SINGLE_FILE_MAX_SIZE = 20L * 1024 * 1024;

    // ==================== 通用文件上传（/api/file/upload） ====================

    /** 单文件大小上限：20MB */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** 支持的文件扩展名（图片 + 文档） */
    public static final java.util.Set<String> ALLOWED_FILE_EXTENSIONS =
            java.util.Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt");

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

    // ==================== 历史记录（/api/history） ====================

    /** 默认历史记录查询条数 */
    public static final int HISTORY_LIST_DEFAULT_LIMIT = 10;
}
