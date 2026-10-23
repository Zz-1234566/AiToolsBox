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

    /** 单文件大小上限（字节）：20MB（与 spring.servlet.multipart.max-file-size 保持一致） */
    public static final long BATCH_SINGLE_FILE_MAX_SIZE = 20L * 1024 * 1024;

    // ==================== 通用文件上传（/api/file/upload） ====================

    /** 单文件大小上限：20MB */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * 「文件大小超过 N MB」的错误文案（按字节上限生成）。
     *
     * <p><b>为什么要这个方法</b>：文案里硬编码 "20MB" 时，改了
     * {@link #MAX_FILE_SIZE} 文案不会跟着变 → 用户看到的提示与实际限制对不上
     * （改大后仍提示 20MB，改小后仍提示 20MB）。全部走本方法即可保证同源。
     *
     * @param limitBytes 限制字节数
     * @return 形如「文件大小不能超过20MB」的中文文案
     */
    public static String fileSizeLimitMessage(long limitBytes) {
        return fileSizeLimitMessage("文件大小", limitBytes);
    }

    /**
     * 同 {@link #fileSizeLimitMessage(long)}，但可指定主语。
     *
     * @param subject    主语，如「背景图」「批量文件总大小」
     * @param limitBytes 限制字节数
     * @return 形如「背景图不能超过20MB」的中文文案
     */
    public static String fileSizeLimitMessage(String subject, long limitBytes) {
        return subject + "不能超过" + (limitBytes / 1024 / 1024) + "MB";
    }

    /** 支持的文件扩展名（图片 + 文档 + 音频） */
    public static final java.util.Set<String> ALLOWED_FILE_EXTENSIONS =
            java.util.Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt",
                    "mp3", "wav", "m4a", "aac", "flac", "ogg", "amr");

    // ==================== 图片扩展名（各工具支持范围不同，勿随意合并） ====================

    /**
     * 图片扩展名全集：jpg / jpeg / png / bmp / gif / webp。
     *
     * <p>用于 doc-to-text（图片直送 OCR）、文件压缩（图片通道）两条链路。
     *
     * <p><b>注意</b>：另有两个工具的支持范围**比本集小**，是有意为之，不是遗漏：
     * {@link #SEGMENTATION_IMAGE_EXTS}（抠图，少 webp）、
     * {@link #AI_FILE_READER_IMAGE_EXTS}（文件解读，少 bmp）。
     */
    public static final java.util.Set<String> IMAGE_EXTS =
            java.util.Set.of("jpg", "jpeg", "png", "bmp", "gif", "webp");

    /**
     * 抠图工具（image-bg-remover）支持的图片扩展名：jpg / jpeg / png / bmp / gif。
     *
     * <p>比 {@link #IMAGE_EXTS} **少 webp** —— 前端 {@code image-bg-remover.vue} 的
     * ALLOWED_EXTS 同样不含 webp，两处必须保持同步（改一处就要改另一处）。
     */
    public static final java.util.Set<String> SEGMENTATION_IMAGE_EXTS =
            java.util.Set.of("jpg", "jpeg", "png", "bmp", "gif");

    /**
     * AI 文件解读（ai-file-reader）支持的图片扩展名：jpg / jpeg / png / gif / webp。
     *
     * <p>比 {@link #IMAGE_EXTS} **少 bmp** —— 该工具的能力范围是产品决策，
     * 在产品拍板前保持既有行为不变。
     */
    public static final java.util.Set<String> AI_FILE_READER_IMAGE_EXTS =
            java.util.Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 文档类白名单：doc-to-text 工具用
     * （txt/docx/pdf 走文字层，图片类走 OCR）。
     *
     * <p>图片部分由 {@link #IMAGE_EXTS} 派生，避免两处各写一份后漂移。
     */
    public static final java.util.Set<String> ALLOWED_DOC_EXTENSIONS =
            java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("pdf", "docx", "txt"),
                            IMAGE_EXTS.stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** 音频类白名单：audio-transcribe 工具用（mp3/wav/m4a/aac/flac/ogg/amr） */
    public static final java.util.Set<String> ALLOWED_AUDIO_EXTENSIONS =
            java.util.Set.of("mp3", "wav", "m4a", "aac", "flac", "ogg", "amr");

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

    /**
     * 批量上传临时目录名（位于系统临时目录下）。
     * <p>批量文件先**流式落盘**到这里，异步任务再逐个读进内存处理 ——
     * 避免 200MB 的整批字节同时驻留堆里（详见 BatchFilePayload 的类注释）。
     */
    public static final String BATCH_TEMP_DIR_NAME = "aitools-batch-upload";

    /**
     * 单次批量上传的**总大小**上限：200MB（批量 10 个文件 × 单文件 20MB）。
     *
     * <p>⚠️ 这个值必须与 {@code application.yml} 的
     * {@code spring.servlet.multipart.max-request-size} 保持一致 ——
     * multipart 的 {@code max-request-size} 是**整个请求**的硬上限，
     * 超过时 Spring 在进入 Controller 之前就抛
     * {@code MaxUploadSizeExceededException}，本常量对应的校验**根本走不到**。
     *
     * <p><b>三处必须同步</b>（任一小于实际值，用户就会遇到「前端放行、后端拒收」）：
     * <ol>
     *   <li>本常量；</li>
     *   <li>{@code application.yml} 的 {@code max-request-size}；</li>
     *   <li>前端 {@code config/tools.js} 的 {@code fileRule.maxTotalSize} 与文案。</li>
     * </ol>
     *
     * <p><b>内存安全不靠这个上限</b>：批量文件先流式落盘（{@code BatchUploadService}），
     * 再逐个读进内存处理，所以峰值堆 = 并发批量数 × 单文件大小（20MB），
     * 与总大小上限无关。详见 {@code BatchFilePayload} 的类注释。
     */
    public static final long BATCH_MAX_TOTAL_SIZE = 200L * 1024 * 1024;

    // ==================== 工作流 ====================

    /** 工作流最大层数（拓扑分层后 level 不得超过此值） */
    public static final int WORKFLOW_MAX_DEPTH = 5;

    /** 工作流同层节点并行执行上限 */
    public static final int WORKFLOW_MAX_PARALLEL = 4;

    /** 工作流名称最大长度 */
    public static final int WORKFLOW_NAME_MAX_LENGTH = 64;
}
