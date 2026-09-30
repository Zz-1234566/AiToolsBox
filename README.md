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

工具以 `sys_aitools_tool` 为唯一数据源（`sort_no` 1-15 连续），前端 `src/config/tools.js` 与之 1:1 对应。

| sort_no | 工具编码 | 名称 | 输入类型 | 输出类型 | 接口 |
|---|---|---|---|---|---|
| 1 | `work-summary` | 工作总结 | 文本/文件 | 文本 | `POST /api/ai-office/work-summary`（同步）<br>`POST /api/ai-office/work-summary/stream`（SSE） |
| 2 | `doc-keypoint-extract` | 文档重点提取 | 文件 | 文本 | `POST /api/ai-office/document-summary/stream`（单文件 SSE）<br>`POST /api/ai-office/document-summary/batch-upload` + `GET /api/ai-office/batch/{batchId}/completed`（多文件批量轮询） |
| 3 | `weekly-report` | 周报生成 | 文本/文件 | 文本 | `POST /api/ai-office/weekly-report/stream`（SSE） |
| 4 | `meeting-minutes` | 会议纪要 | 文本 | 文本 | `POST /api/ai-office/meeting-minutes/decide-route`（路由决策）<br>`POST /api/ai-office/meeting-minutes/stream`（SSE）<br>`POST /api/ai-office/meeting-minutes/json`（结构化） |
| 5 | `ocr-recognize` | 智能识别 | 图片 | 文本 | `POST /api/ai-office/ocr-recognize/stream`（单图 SSE）<br>`POST /api/ai-office/ocr-recognize/batch-upload`（多图批量） |
| 6-12 | `id-photo-bg-change` / `portrait-bg-replace` / `image-compress` / `qr-code-gen` / `todo-list` / `pomodoro` / `password-gen` | 图片创意 & 效率小工具 | — | — | **已入库但后端接口未实现**，前端 `realized: false`，点击提示"开发中" |
| 13 | `ai-file-reader` | AI 文件解读 | 图片/文档/文本 | 文本 | `POST /api/ai-office/ai-file-reader/batch-upload`（多模态解读任意文件） |
| 14 | `audio-transcribe` | 录音转写 | 音频 | 文本 | `POST /api/ai-office/meeting-minutes/transcribe`（ffmpeg 转码 + MiniMax chatAudio） |
| 15 | `doc-to-text` | 文档转文本 | 文档 | 文本 | `POST /api/ai-office/doc-to-text`（纯解析，不调 AI） |

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

## ⚠️ 已知问题 / 待处理

### 1. COS 签名 URL 有效期偏短（工作流运行时可能失效）

**现象**：上传文件的接口返回的是**带签名的临时 URL**（`?sign=...&q-sign-time=...`），有效期由 `cos.signed-url-ttl-seconds` 控制，**默认仅 300 秒（5 分钟）**。

**影响场景**：工作流（`workflow`）运行时，后端按用户上传时返回的 `fileUrl` 去读取文件。若用户**上传后停留超过 5 分钟再点「开始运行」**，签名过期 → 读文件 403 → 节点执行失败。

**当前状态**：暂不处理（正常操作节奏下 5 分钟足够）。

**可选修复方向**（按侵入性排序）：
1. 调大 `cos.signed-url-ttl-seconds`（如 3600）—— 改配置即可，但链接长期有效有安全考量
2. **根治**：工作流节点参数改为存 COS **对象 key**（如 `file/1/xxx.txt`），运行时由后端用 SecretId/SecretKey 直连 COS 读取，不依赖签名 URL
3. 上传后立即运行（产品层面规避）

---

## 许可

