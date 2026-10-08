# 智汇工具箱（AIToolsBox）

> 毕业设计项目：基于 uni-app + Spring Boot 的多端 AI 工具聚合应用

## 项目简介

一个面向移动端 / H5 的 AI 工具箱 App，内置工作总结、文档重点提取等 AI 办公能力，并预留了图片处理、效率小工具的扩展位。文本类工具调用 MiniMax M2.7（OpenAI 兼容协议），结果支持 SSE 流式打字机效果。

## 技术栈

### 后端（`code/aiTools_backend`）
- Spring Boot 3.2.5 + Java 17
- MyBatis-Plus 3.5.5 + MySQL 8
- Spring Data Redis + JWT 鉴权（jjwt 0.12.5）
- 腾讯云 COS SDK（文件存储，可切本地）
- Apache PDFBox + POI（PDF / Word 文档解析）
- Hutool 工具库

### 前端（`code/aiTools_frontend`）
- uni-app + Vue 3 + Vite
- 多端输出：移动 App（uni-app-plus）、H5
- vue-i18n（国际化）

## 仓库结构

```
Graduation project/
├── code/
│   ├── aiTools_backend/          # 后端 Spring Boot 工程
│   │   ├── sql/init.sql          # 数据库初始化脚本
│   │   └── src/main/resources/
│   │       ├── application.yml                # 公共配置
│   │       └── application-dev.yml.example    # 开发环境配置模板（占位符）
│   └── aiTools_frontend/         # 前端 uni-app 工程
├── App图标/                      # App 图标资源
├── 默认头像/                     # 默认头像资源
└── README.md                     # 本文件
```

> **说明**：`application-dev.yml`、CSV 凭证文件、`qq邮箱授权码.txt` 等含敏感信息的内容**不进入仓库**（`.gitignore` 已排除或提交前需手动清理）。

## 本地启动

### 1. 准备基础服务

| 服务 | 版本 | 用途 |
|---|---|---|
| JDK | 17+ | 后端运行 |
| Maven | 3.8+ | 后端构建 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 7.0+ | 验证码 / 限流等 |
| Node.js | 18+ | 前端构建（uni-app） |
| HBuilderX | 最新版 | 前端 IDE（推荐，可视化运行多端） |

### 2. 初始化数据库

```bash
# 登录 MySQL 后执行
mysql -u root -p < code/aiTools_backend/sql/init.sql
```

脚本会创建 `ai_toolbox` 库及全部业务表，并写入 14 个初始化工具（详见 sys_aitools_tool 表，sort_no 1-14 唯一连续）及对应系统提示词。

### 3. 配置后端

```bash
# 复制模板为本地配置
cp code/aiTools_backend/src/main/resources/application-dev.yml.example \
   code/aiTools_backend/src/main/resources/application-dev.yml
```

按下方"环境变量清单"设置好本地环境变量，然后**保留 `application-dev.yml` 不再修改**（也可直接在 yml 里把 `${ENV:default}` 替换为字面量，但优先用环境变量）。

### 4. 启动后端

```bash
cd code/aiTools_backend
mvn spring-boot:run
# 默认监听 http://localhost:8080
```

### 5. 启动前端

**方式 A：HBuilderX（推荐）**
1. 用 HBuilderX 打开 `code/aiTools_frontend` 目录
2. 运行 → 运行到内置浏览器 / 微信小程序 / 手机或模拟器

**方式 B：命令行 H5**
```bash
cd code/aiTools_frontend
npm install
npm run dev:h5
# 默认 http://localhost:3000
```

## 环境变量清单

后端通过 `${ENV_VAR:default}` 形式从环境变量读取敏感配置（避免硬编码泄露）。**`application-dev.yml` 已在 `.gitignore` 中排除，提交仓库时不会被上传。**

| 变量名 | 必填 | 用途 | 获取方式 |
|---|---|---|---|
| `MYSQL_PASSWORD` | ✅ | MySQL root 密码 | 本地自设 |
| `MAIL_USERNAME` | ✅ | 发件邮箱（如 QQ 邮箱） | 自有邮箱 |
| `MAIL_PASSWORD` | ✅ | QQ 邮箱 SMTP **授权码**（非登录密码） | 邮箱设置 → 账户 → POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务 |
| `JWT_SECRET` | ✅ | JWT 签名密钥 | 本地生成一段 32+ 字节随机字符串 |
| `COS_SECRET_ID` | ⛔ 可选 | 腾讯云 COS SecretId | 腾讯云控制台 → 访问管理 → API 密钥管理 |
| `COS_SECRET_KEY` | ⛔ 可选 | 腾讯云 COS SecretKey | 同上 |
| `COS_BUCKET` | ⛔ 可选 | 腾讯云 COS **桶名**（如 `ai-tools-box-1419900334`）。`cos.enabled=true` 时**必填**，缺失会回退到占位符桶名导致上传失败 | 腾讯云控制台 → 对象存储 → 存储桶列表 |
| `COS_REGION` | ⛔ 可选 | COS 地域（如 `ap-guangzhou`），需与桶所在地域一致 | 同上 |
| `AI_MINIMAX_TEXT_API_KEY` | ✅ | MiniMax（OpenAI 兼容）API Key，用于文本类办公工具 | MiniMax 开放平台 → API Keys |

> **PowerShell 临时设置示例**（当前会话有效）：
> ```powershell
> $env:MYSQL_PASSWORD = "your_password"
> $env:JWT_SECRET = "your-32-byte-random-string"
> # ... 一次性设完所有变量后，再 mvn spring-boot:run
> ```

> **永久设置**：Windows 系统属性 → 环境变量 → 用户变量。

### 非敏感配置

以下信息可硬编码在 `application-dev.yml`，不属于密钥：

- `spring.datasource.url`（MySQL 连接串）
- `spring.mail.host: smtp.qq.com` / `port: 465`
- `cos.region: ap-guangzhou`
- `ai.minimax-text.api-url: https://api.minimaxi.com/v1/text/chatcompletion_v2`（官方 V2 文本入口）
- `ai.minimax-text.model: MiniMax-M2.7`

## 已实现的 AI 工具

工具以 `sys_aitools_tool` 为唯一数据源（`sort_no` 1-6 连续），前端 `src/config/tools.js` 与之 1:1 对应。

工具划分为两层，**目的是让每个工具都能作为工作流的原子节点自由编排**：

| 层 | 职责 | 工具 |
|---|---|---|
| **解析层** | 纯转换，**不调用 AI** | 文档提取、录音转写 |
| **加工层** | 调文本大模型，**只吃文字** | 会议纪要、重点提取、工作总结、周报生成 |

| sort_no | 工具编码 | 名称 | 层 | 输入 | 输出 | 接口 |
|---|---|---|---|---|---|---|
| 1 | `doc-to-text` | 文档提取 | 解析 | 文件 | 文本 | `POST /api/ai-office/doc-to-text`（纯解析） |
| 2 | `audio-transcribe` | 录音转写 | 解析 | 音频 | 文本 | `POST /api/ai-office/meeting-minutes/transcribe`（ffmpeg + **双引擎**）<br>`POST /api/ai-office/meeting-minutes/batch-transcribe`（多文件批量） |
| 3 | `meeting-minutes` | 会议纪要 | 加工 | 文本 | 文本 | `POST /api/ai-office/meeting-minutes/decide-route`（路由决策）<br>`POST /api/ai-office/meeting-minutes/stream`（SSE）<br>`POST /api/ai-office/meeting-minutes/json`（结构化） |
| 4 | `doc-keypoint-extract` | 重点提取 | 加工 | 文本 | 文本 | `POST /api/ai-office/document-summary/text-stream`（SSE，纯文字） |
| 5 | `work-summary` | 工作总结 | 加工 | 文本 | 文本 | `POST /api/ai-office/work-summary/stream`（SSE） |
| 6 | `weekly-report` | 周报生成 | 加工 | 文本 | 文本 | `POST /api/ai-office/weekly-report/stream`（SSE） |

### 文档提取的三条通道

`doc-to-text` 按文件类型分流，**实际使用的通道**写入响应字段 `method`（`text-layer` / `ocr`）：

| 输入 | 通道 | 实现 |
|---|---|---|
| `txt` | `text-layer` | 编码自适应（UTF-8 失败回退 GBK） |
| `docx` | `text-layer` | POI 抽段落 + 表格 |
| `pdf`（文本型） | `text-layer` | PDFBox 抽文字层 |
| `pdf`（扫描件，**抽不到文字层**） | `ocr` | 逐页渲染成 PNG（150 DPI，最多 20 页）后送腾讯云 OCR |
| `png` / `jpg` / `jpeg` / `bmp` / `gif` / `webp` | `ocr` | 直接送腾讯云 OCR |

> **为什么扫描件要自己渲染**：腾讯云 `GeneralAccurateOCR` 官方注释明确「支持 PNG、JPG、JPEG、BMP」，
> 其 OCR 模块下不存在任何 Pdf Request 类（已核实 SDK 3.1.270 全量 class），故扫描件 PDF 必须先转图片。
> 20 页上限用于控制 OCR 按量计费成本。

### 提示词设计

加工层 4 个工具各 2 条系统提示词（`sys_ai_prompt` 的 `prompt_use` 区分），共 8 条：

| `prompt_use` | 作用 | 特点 |
|---|---|---|
| `format` | 约束**输出结构** | 规定章节、编号、表格、字数上限 |
| `generate` | 规定**角色与处理步骤** | 怎么读、提炼什么、什么不能编造 |

两者分离，用户可只改其中一项。`getDefaultByUse` 按 `id` 升序取第一条作为该工具的系统默认，
故迁移脚本统一「先逻辑删除旧的 → 再插入新的」（见 `sql/migrate_prompt_*.sql`）。

**已删除的工具**（`dr=1`，保留可回滚）：`ocr-recognize`（能力并入 `doc-to-text`）、
`ai-file-reader`（多模态与「加工层只吃文本」冲突）、图片创意 4 个与效率小工具 3 个（一直未实现）。

> **说明**：`bank-receipt-recognize` / `invoice-recognize` **不是工具**，它们仅作为**系统提示词**存在于 `sys_ai_prompt` 表（供 OCR 场景复用），不在 `sys_aitools_tool` 登记。

### 工具输入类型说明

`sys_aitools_tool.input_type` / `output_type` 为**逗号分隔的枚举集合**（`none`/`text`/`audio`/`document`/`image`/`file`），用于工作流节点连线时的类型匹配（`upstream.output_type ⊆ downstream.input_type`）。

## 工作流模块

用户可把多个工具编排成流水线：**上游节点输出 → 下游节点输入**，一次运行跑完整条链。

- **节点 = 工具**：节点引用 `tool_code`，参数（提示词等）存 `params` JSON
- **数组语义**：N 个输入 → N 个输出，逐项独立处理（并行汇聚时各上游输出保持分离，不合并字符串）
- **执行模型**：按 `deps` 拓扑分层，同层并行，**最多 5 层**
- **类型校验**：保存与运行双重校验连线类型匹配
- **提示词**：节点参数只存 `promptIdFormat` / `promptIdGenerate`（跟随提示词更新）；提示词失效时**明确报错提示重选**，不静默回退

| 接口 | 说明 |
|---|---|
| `POST /api/workflow/save` | 保存（新建/覆盖），保存即校验 |
| `GET /api/workflow/list` | 列表（含节点信息） |
| `GET /api/workflow/{workflowId}` | 详情 |
| `DELETE /api/workflow/{workflowId}` | 删除 |
| `POST /api/workflow/{workflowId}/run` | 运行（同步返回结果） |
| `GET /api/workflow/runs` | 运行历史列表 |
| `GET /api/workflow/runs/{runId}` | 运行详情（含每节点输入输出） |

**相关表**：`sys_workflow`（工作流定义）、`sys_workflow_run`（运行历史）；DDL 见 `code/aiTools_backend/sql/init_workflow.sql`。

## 其他模块

| 模块 | 接口前缀 | 说明 |
|---|---|---|
| 用户 | `/api/user` | 登录/注册/找回账号/改密/资料（JWT 鉴权） |
| 历史记录 | `/api/history` | 工具调用历史（主表 + 明细 + 文件） |
| 提示词 | `/api/prompt` | 系统提示词查询 + 用户自定义提示词 CRUD + AI 生成 |
| 收藏 | `/api/favorite` | 工具/提示词收藏（`sys_user_favorite`，DDL 见 `sql/init_favorite.sql`） |
| 文件 | `/api/file/upload` | 通用文件上传（COS / 本地，按 `prefix` 分目录） |

## 用户角色与权限

用户角色存于 `sys_user.role`（`VARCHAR(16)`），取值 **`admin`**（管理员）/ **`user`**（普通用户）。新注册用户默认 `user`。

### 权限矩阵

| 能力 | admin | user |
|---|---|---|
| 使用所有 AI 工具 / 工作流 | ✅ | ✅ |
| 查看系统预制提示词（`/api/prompt/system/list`） | ✅ | ✅ |
| 管理**自己的**提示词（`sys_ai_user_prompt`，增/删/改） | ✅ | ✅ |
| 管理系统**预制**提示词（`sys_ai_prompt`，增/删/改） | ✅ | ⛔ **403** |

> 权限为**双层防护**：前端按角色显隐入口（体验），后端 `AuthUtil.requireAdmin()` **强制校验**（安全）。
> 后端校验实时查库，**调整角色立即生效**，无需重新登录。
> 前端角色来自登录响应 `userInfo.role`，改角色后前端需重新登录才刷新显示。

### 设为管理员

新环境初始化后无内置账号（用户靠注册），需手动提权：

```sql
UPDATE sys_user SET role = 'admin' WHERE account = 'AIT00000000';
```

验证：

```sql
SELECT id, account, username, role FROM sys_user WHERE dr = 0;
```


## ⚠️ 密钥安全提醒

1. **提交前请确认 `application-dev.yml` 没有出现在 `git status` 中**——它已被 `.gitignore` 排除，但仍要复核。
2. **若曾在其他平台 / 旧仓库提交过任何含真实密钥的文件，请立即轮换**：
   - QQ 邮箱授权码
   - 腾讯云 COS SecretId / SecretKey
   - MiniMax API Key（文本模型）
   - JWT Secret
3. **提交后**仍建议轮换一次——GitHub 即使删除 commit，历史中仍可恢复。
4. 腾讯云子账号请使用 **最小权限策略**（仅授权所需存储桶的读写）。

## 录音转写双引擎（MiniMax / 腾讯云）

`audio-transcribe` 支持两个转写通道，前端在录音转写页可手动选择，整批文件共用同一引擎。

| 引擎 | 标识 | 实现 | 特点 |
|---|---|---|---|
| MiniMax | `minimax` | `MinimaxClient.chatAudio`（M3 多模态） | 默认通道，通用转写 |
| 腾讯云 | `tencent` | 云 API `CreateRecTask`（录音文件识别）+ `DescribeTaskStatus` 轮询 | 支持**热词表**，术语识别更准 |

**调用方式**（两个接口都支持，不传则用 `asr.engine` 配置值，默认 `minimax`）：

```bash
curl -X POST "http://localhost:8080/api/ai-office/meeting-minutes/transcribe?engine=tencent" \
     -H "Authorization: Bearer $TOKEN" -F "file=@audio.mp3"
```

**配置**（`.env`，`application-dev.yml` 的 `asr.tencent` 段读取）：

```
ASR_TENCENT_ENABLED=true        # 是否启用腾讯云通道
ASR_APP_ID=                     # 腾讯云语音识别控制台 →「账户信息」（ASR 专用）
ASR_SECRET_ID=                  # 可选，留空自动复用 COS_SECRET_ID
ASR_SECRET_KEY=                 # 可选，留空自动复用 COS_SECRET_KEY
```

**降级规则**（`TranscribeService#resolveEngine`）：

- 前端传 `engine=tencent` 但 `enabled=false` 或 `ASR_APP_ID` 为空 → **自动回落 minimax**，不报错
- 传入无法识别的引擎值 → 兜底 minimax
- 工作流引擎（`NodeExecutor`）暂不支持指定引擎，统一走默认引擎

**实测数据**（30 秒中文音频，2026-10-08）：

| 引擎 | 耗时 | 返回格式 |
|---|---|---|
| 腾讯云 | 5.3s | 带 `[起始时间,结束时间]` 时间戳前缀 |
| MiniMax | 1.6s | 纯文本，无时间戳 |

> **腾讯云通道的 3.7MB 限制**：`CreateRecTask` 走 `SourceType=1` 内联 base64 时 `Data` 字段上限 5MB，
> 反推原始音频上限约 3.7MB（`asr.tencent.max-audio-bytes`）。
> 超限时由 `FileCompressService` 自动降码率压缩后重试（最多 5 轮），压不动才返回中文错误提示
> 「音频文件过大…」，不会把腾讯云的英文错误码透给用户。详见下节。

## 文件压缩组件（图片 / 音频）

`FileCompressService` 把超过第三方接口字节限制的文件压到限额以内，供 AI 调用前使用。

| 类别 | 扩展名 | 手段 | 迭代参数 |
|---|---|---|---|
| **图片** | jpg/jpeg/png/bmp/gif/webp | ImageIO 降质 + 等比缩放 | 质量 0.75 起每轮 −0.05（下限 0.1）；宽度 1600 起每轮 ×0.8（下限 600）|
| **音频** | mp3/wav/m4a/aac/flac/ogg/amr/opus/wma | ffmpeg 降码率 | 逐档 `64k → 48k → 32k → 24k → 16k` |
| **文档** | pdf/doc/docx/txt | **不压缩** | 走 `DocumentParser` 本地抽文字层，与第三方字节限制无关 |

**失败语义**：迭代用尽仍超限 → 抛 `BusinessException`（`FILE_TOO_LARGE`），**不回退原文件**。
理由：回退后调用方必然在第三方接口处再次失败，用户只会看到「未知错误」。

**设计依据**（参考 GitHub 成熟实现）：
- 迭代采用 `while` + 单调递减 + 明确下界，**不做递归**，结构上不可能死循环
  （Stirling-PDF 93k★ / Tiny / EasyImageCompressor 均为此模式）
- 图片质量下限 0.1 有依据：IJG 官方 FAQ 指出 Q10 以下接近「op art」，再降无意义
- 音频**不引入** `ffmpeg-cli-wrapper`（其 `setTargetSize` 的音视频混合码率拆分源码中仍为 TODO，
  且项目规范为「未经允许不引入新依赖」）

**与 `transcodeToMp3` 的边界**（勿混淆）：

| 组件 | 职责 | 触发 |
|---|---|---|
| `TranscribeService#transcodeToMp3` | **格式归一化** | 无条件执行，保证 AI 能识别 |
| `FileCompressService` | **大小控制** | 仅超限时执行 |

**实测数据**（2026-10-08）：

| 场景 | 结果 |
|---|---|
| 9.67MB WAV → 限额 3.7MB | 转码归一化后 1.84MB 提交，识别成功（489 字）|
| 10.9MB MP3 → 限额 3.7MB | 压缩组件 5 轮 `16k` 收敛到 2.73MB，识别成功 |
| 7.33MB 噪声 PNG → 200KB | 4 轮收敛到 138KB（宽度 655px、质量 0.55）|
| 30 分钟音频降码率对照 | 64k→13.7MB、32k→6.9MB、16k→3.4MB（末档刚好达标）|

> ⚠️ Spring 上传限制 20MB 是第一道防线，组件只处理 20MB 以内但超出第三方限额的部分。
> 38MB 音频会在上传阶段被拒（`文件大小不能超过20MB`），不会走到压缩环节。

## AI 内容落库截断

`HistoryServiceImpl` 在写库前对 `input_content` / `output_content` 截断，
防止超长 AI 输出写满 MySQL `text` 的 65535 **字节**上限。

| 常量 | 值 | 依据 |
|---|---|---|
| `Constants.AI_INPUT_MAX_LENGTH` | 20000 | 喂模型的 prompt 上限（`DocumentParser` 共用）|
| `Constants.AI_OUTPUT_MAX_LENGTH` | 16000 | 落库上限。UTF-8 最坏 4 字节/字符（emoji），16000×4=64000 < 65535 |

**实现要点**：阈值按 `String.length()`（UTF-16）判定，切分点用 `offsetByCodePoints` 求出，
保证不从代理对中间切开产生乱码；超限追加「【内容过长，已截断】」标记。

## ⚠️ 已知问题 / 待处理

### 1. COS 签名 URL 有效期偏短（工作流运行时可能失效）

**现象**：上传文件的接口返回的是**带签名的临时 URL**（`?sign=...&q-sign-time=...`），有效期由 `cos.signed-url-ttl-seconds` 控制，**默认仅 300 秒（5 分钟）**。

**影响场景**：工作流（`workflow`）运行时，后端按用户上传时返回的 `fileUrl` 去读取文件。若用户**上传后停留超过 5 分钟再点「开始运行」**，签名过期 → 读文件 403 → 节点执行失败。

**当前状态**：暂不处理（正常操作节奏下 5 分钟足够）。

**可选修复方向**（按侵入性排序）：
1. 调大 `cos.signed-url-ttl-seconds`（如 3600）—— 改配置即可，但链接长期有效有安全考量
2. **根治**：工作流节点参数改为存 COS **对象 key**（如 `file/1/xxx.txt`），运行时由后端用 SecretId/SecretKey 直连 COS 读取，不依赖签名 URL
3. 上传后立即运行（产品层面规避）

### 2. 历史记录未保存文件（`sys_aitools_history_file` 始终为空）

**现象**：查看历史记录详情时，**看不到当次使用的文件**（文件名/下载）。数据库 `sys_aitools_history_file` 表**始终没有数据**。

**原因**：文件类工具（文档重点提取、智能识别、AI 文件解读、文档转文本、录音转写）当前是**把文件直传后端**（multipart），后端解析完即丢弃，**既不上传 COS、也不写 `history_file` 表**。表结构完整，但写入方法 `HistoryService#recordWithFiles` **无任何调用点（死代码）**。

**当前状态**：暂不处理。历史详情页只展示文本（输入/结果/错误/提示词/耗时），文件类记录提示"文件请重新上传"。

**若要做，方案如下**：
1. 工具执行处调 `fileStorageService.store(file, "file/{userId}")` 上传 COS，拿到 **object key**
2. `history_file` 表**存 key**（不是签名 URL——签名 URL 仅 5 分钟有效，存了也没用）
3. 查询历史时由后端**实时重新签发**（`generatePresignedUrl` 是纯本地 HMAC 计算，无网络开销，无需判断是否过期）
4. 前端详情页显示文件名，点击用该次签发的 URL 下载

**注意**：`history_file.role` 语义为 **1输入 / 2输出**（原建表注释 0/1 与 Java 代码 1/2 冲突，已统一为后者）。

**降低优先级的原因**：文件类工具是少数，且规划中工具将逐步改为**前端解析后只传文本**，届时该表可能废弃。

---

## 许可

