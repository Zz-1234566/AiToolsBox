# 智汇工具箱（AIToolsBox）

> 毕业设计项目：基于 uni-app + Spring Boot 的多端 AI 工具聚合应用

## 项目简介

一个面向移动端 / H5 的 AI 工具箱 App，当前已实现 **7 个工具**：文档提取、录音转写、会议纪要、重点提取、工作总结、周报生成（AI 办公），以及图片背景去除（本地 ONNX 抠图，不走云端 AI）。加工层工具与工作流节点调用 DeepSeek V4.1 Flash（OpenCode Go 套餐，OpenAI 兼容协议），图片理解走同一多模态模型，录音转写走腾讯云 ASR，结果支持 SSE 流式打字机效果。各工具可被编排成**工作流流水线**一次跑完。

## 技术栈

### 后端（`code/aiTools_backend`）
- Spring Boot 3.2.5 + Java 17
- MyBatis-Plus 3.5.5 + MySQL 8
- Spring Data Redis + JWT 鉴权（jjwt 0.12.5）
- 腾讯云 COS SDK（文件存储，可切本地）
- Apache PDFBox + POI（PDF / Word 文档解析）
- ONNX Runtime（人像分割，本地 CPU 离线推理）
- 腾讯云 OCR / 语音识别 / COS SDK（云端能力）
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
│   │   ├── sql/                  # 建库脚本（只有 4 个被 git 跟踪，见下方说明）
│   │   └── src/main/resources/
│   │       ├── application.yml                # 公共配置
│   │       ├── application-dev.yml.example    # 开发环境配置模板（占位符）
│   │       └── models/                        # 本地 ONNX 抠图模型（不入库，需自行下载）
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

`sql/` 目录里大部分临时脚本被 `.gitignore` 排除，**入库的是「建库入口 + 版本化迁移」**
（`!sql/init.sql` + `!sql/v1_*.sql`）。已有的库需要**按顺序把迁移脚本补齐**，
只跑 `init.sql` 会缺表/缺列：

| # | 脚本 | 作用 |
|---|---|---|
| 1 | `sql/init.sql` | 建库 `ai_toolbox` + 13 张表 + 工具/提示词/工作流初始数据 |
| 2 | `sql/v1_2026_10_12_ai_model_upgrade.sql` | 建 `sys_user_ai_model`（用户自选 AI 模型，**AI 模型选择功能依赖此表**） |
| 3 | `sql/v1_2026_10_12_ai_model_seeds.sql` | 系统模型注册表 `sys_ai_model` 的初始数据（历史种子，具体模型以实际配置为准） |
| 4 | `sql/v1_2026_10_12_ai_model_deepseek_seed.sql` | DeepSeek 模型注册（BYOK，Key 字段全为 NULL，用户在「我的 AI 模型」页自配） |
| 5 | `sql/v1_2026_10_12_image_bg_remover_seed.sql` | 图片背景去除工具的 seed |
| 6 | `sql/v1_2026_10_26_batch_task_error_msg.sql` | `sys_batch_task` 加 `error_msg` 列（**批量任务依赖此列**，见下） |

```bash
# 登录 MySQL 后依次执行
mysql -u root -p < code/aiTools_backend/sql/init.sql
mysql -u root -p < code/aiTools_backend/sql/v1_2026_10_12_ai_model_upgrade.sql
mysql -u root -p < code/aiTools_backend/sql/v1_2026_10_12_ai_model_seeds.sql
mysql -u root -p < code/aiTools_backend/sql/v1_2026_10_12_ai_model_deepseek_seed.sql
mysql -u root -p < code/aiTools_backend/sql/v1_2026_10_12_image_bg_remover_seed.sql
mysql -u root -p < code/aiTools_backend/sql/v1_2026_10_26_batch_task_error_msg.sql
```

> ⚠️ **漏执行第 5 个脚本的后果**：`sys_batch_task` 缺 `error_msg` 列 →
> 批量任务落库时抛 `Unknown column 'error_msg' in 'field list'` →
> **所有批量任务全线不可用**（不只是去背景，所有批量工具都受影响）。
> 应用启动时会做 **schema 自检**，缺列会直接在日志里告诉你跑哪个脚本
> （搜 `[schema-check]`），不用等运行时才炸。
>
> 单独补这一列也可以（脚本是幂等的）：
> ```sql
> ALTER TABLE sys_batch_task ADD COLUMN error_msg VARCHAR(255) DEFAULT NULL
>   COMMENT '失败原因（如僵尸任务被自动回收、服务异常中断）' AFTER status;
> ```

建库后可自检：

```bash
grep -c "INSERT INTO.*sys_aitools_tool" code/aiTools_backend/sql/init.sql   # 15
grep -c "INSERT INTO.*sys_ai_prompt"   code/aiTools_backend/sql/init.sql   # 16
```

> ⚠️ 本地还有 `v1_2026_10_12_ai_model_deepseek_seed.sql`（DeepSeek 模型 seed）**未入库**，
> 换机器部署会丢失 DeepSeek 选项，需要的话手动执行或补进仓库。

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

> **生产环境请显式指定堆大小**（`-Xmx`），批量上传的并发上限是按堆大小算出来的：
> ```bash
> java -Xmx2g -jar aiTools_backend-1.0.0-SNAPSHOT.jar
> ```
> 默认 JVM 堆是物理内存的 1/4，容器里容易被算小，导致批量任务并发一高就 OOM。
> 详见下方「批量上传的内存与并发」。

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

#### H5 端口冲突自查（打开是别人的 401 页面？）

浏览器打开 `http://localhost:3000` 却看到一个**陌生服务**返回的 401（响应体形如
`{"status":"failed","retcode":1401,...}`，字段名不是本项目的 `code`/`message`），
说明 **3000 端口被本机另一个程序占了**——Windows 上 `0.0.0.0:3000` 与 `127.0.0.1:3000`
可以分别绑定同一个端口，浏览器解析 `localhost` 优先走 `127.0.0.1`，于是打到了那个程序上。

```bash
# 1) 看 3000 端口都被谁占了（Windows）
netstat -ano | grep ":3000" | grep LISTENING
# 2) 确认那个 PID 是什么程序
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter 'ProcessId=<上一步的PID>' | Select-Object ProcessId,CommandLine"
```

**三种解法**（任选其一）：

| 做法 | 怎么做 |
|---|---|
| ① 换端口启动前端（最省事） | `VITE_DEV_PORT=5173 npm run dev:h5`，然后访问 `http://localhost:5173` |
| ② 关掉占用 3000 的程序 | 找到上面的 PID，`taskkill /PID <PID> /F` |
| ③ 用局域网 IP 访问 | dev server 绑的是 `0.0.0.0`，用 `http://<本机IP>:3000`（如 `http://10.14.0.69:3000`）访问，会绕过 `127.0.0.1` 上那个程序 |

> 手机连同一个 Wi-Fi 调试 App 时**必须**用 ③ 里的局域网 IP ——
> 手机上的 `localhost` 指的是手机自己。后端地址在 App 内「设置」页填同一个 IP 即可。

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
| `AI_LLM_API_URL` / `AI_LLM_API_KEY` / `AI_LLM_MODEL` | ✅ | 文本与多模态模型（OpenCode Go 套餐，OpenAI 兼容协议）。默认模型 `deepseek-v4.1-flash`，接口 `https://opencode.ai/inference/go/v1/chat/completions` | OpenCode Console → API Key |
| `AI_OPENCODEGO_API_URL` / `AI_OPENCODEGO_API_KEY` / `AI_OPENCODEGO_MODEL` | ✅ | 智能体（ReAct 循环）专用模型，同上套餐；`/source` 需支持工具调用 | 同上 |
| `ASR_TENCENT_ENABLED` / `ASR_APP_ID` / `ASR_SECRET_ID` / `ASR_SECRET_KEY` / `ASR_REGION` | ⛔ 可选 | 腾讯云语音识别通道；`ASR_SECRET_*` 留空自动复用 `COS_SECRET_*` | 腾讯云语音识别控制台 |
| `ASR_FFMPEG_PATH` | ⛔ 可选 | ffmpeg 可执行文件路径，留空走系统 PATH | 自设 |
| `SYS_AI_MODEL_KEY` | ⛔ 可选 | 系统 AI 模型注册表 `sys_ai_model` 的密钥加密主密钥 | 自设 |

> ⚠️ **AI 调用通道**：
> - 文本 / 工作流节点 / 多模态读图 → `ai.text` 与 `ai.vision`（`AI_LLM_*`）
> - 智能体 → `ai.agent`（`AI_OPENCODEGO_*`）
> - 录音转写 → 腾讯云 ASR（`ASR_TENCENT_*`），不经大模型
>
> Go 套餐端点必须是 `https://opencode.ai/inference/go/v1/...`（按量付费端点是 `/inference/openai/v1`），
> 且请求需带 `x-opencode-session` 头，缺失会返回 400 `MissingSessionID`。

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
- `ai.text.api-url: https://opencode.ai/inference/go/v1/chat/completions`
- `ai.text.model: deepseek-v4.1-flash`

## 已实现的 AI 工具

工具以 `sys_aitools_tool` 为唯一数据源，前端 `src/config/tools.js` 与之 1:1 对应
（`CATEGORIES` 中列出的即为当前**已实现并可运行**的工具，共 8 个）。

AI 办公类工具划分为两层，**目的是让每个工具都能作为工作流的原子节点自由编排**：

| 层 | 职责 | 工具 |
|---|---|---|
| **解析层** | 纯转换，**不调用 AI** | 文档提取、录音转写 |
| **加工层** | 调文本大模型，**只吃文字** | 会议纪要、重点提取、工作总结、周报生成 |

| 工具编码 | 名称 | 分类 | 层 | 输入 | 输出 | 接口 |
|---|---|---|---|---|---|---|
| `doc-to-text` | 文档提取 | AI办公助手 | 解析 | 文档 / **图片** | 文本 | `POST /api/ai-office/doc-to-text`（纯解析） |
| `audio-transcribe` | 录音转写 | AI办公助手 | 解析 | 音频（≤10 个） | 文本 | `POST /api/ai-office/meeting-minutes/transcribe`（ffmpeg + **双引擎**）<br>`POST /api/ai-office/meeting-minutes/batch-transcribe`（多文件批量） |
| `meeting-minutes` | 会议纪要 | AI办公助手 | 加工 | 文本 | 文本 | `POST /api/ai-office/meeting-minutes/decide-route`（路由决策）<br>`POST /api/ai-office/meeting-minutes/stream`（SSE）<br>`POST /api/ai-office/meeting-minutes/json`（结构化） |
| `doc-keypoint-extract` | 重点提取 | AI办公助手 | 加工 | 文本 | 文本 | `POST /api/ai-office/document-summary/text-stream`（SSE，纯文字） |
| `work-summary` | 工作总结 | AI办公助手 | 加工 | 文本 | 文本 | `POST /api/ai-office/work-summary/stream`（SSE） |
| `weekly-report` | 周报生成 | AI办公助手 | 加工 | 文本 | 文本 | `POST /api/ai-office/weekly-report/stream`（SSE） |
| `image-bg-remover` | 图片背景去除 | 图片创意工具 | 本地推理 | 图片 | 图片 / 透明底 PNG | `POST /api/ai-office/image-bg-remover`（3 种模式合一：去背景 / 换背景 / 保留主体） |

> 7 个工具分属 2 个分类：AI办公助手 6 个 + 图片创意工具 1 个。
> 图片类工具**不调用任何云端 AI**，由后端 ONNX Runtime 本地 CPU 推理完成（见「图片工具与本地模型」）。

### 已下线的工具（`dr=1`，保留可回滚）

`ocr-recognize`（能力并入 `doc-to-text`）、`ai-file-reader`（多模态与「加工层只吃文本」冲突）、
`id-photo-bg-change`（证件照换背景色：产品上已下线，端点与前端入口均已移除，能力被 `image-bg-remover` 的
`color` 模式完全覆盖）、图片创意 3 个与效率小工具 3 个（未实现）。`init.sql` 中仍保留部分工具的 INSERT 再置 `dr=1`，
是历史顺序的产物，不代表可用。

### 文档提取的三条通道

`doc-to-text` 按文件类型分流，**实际使用的通道**写入响应字段 `method`（`text-layer` / `ocr`）：

| 输入 | 通道 | 实现 |
|---|---|---|
| `txt` | `text-layer` | 编码自适应（UTF-8 失败回退 GBK） |
| `docx` | `text-layer` | POI 抽段落 + 表格，**并对内嵌图片逐张送 OCR 后拼接** |
| `pdf`（文本型） | `text-layer` | PDFBox 抽文字层 |
| `pdf`（扫描件，**抽不到文字层**） | `ocr` | 逐页渲染成 PNG（150 DPI，最多 20 页）后送腾讯云 OCR |
| `png` / `jpg` / `jpeg` / `bmp` / `gif` / `webp` | `ocr` | 直接送腾讯云 OCR |

> **Word 内嵌图片为什么要 OCR**：财务/办公文档常把发票、盖章、签名、整页扫描件截图内嵌在 Word 里，
> 这部分内容**不在文字层中**（POI 抽不到），只能靠 OCR 补齐。
> 实现上先用 `ImageIO` 读图片尺寸过滤掉 < 80×80 的项目符号/装饰图标（避免白花钱），
> 剩余图片逐张送腾讯云 OCR，最多 20 张。该能力参考自实习项目广晟财务共享平台（`WordUtil.processImages`）。

> **为什么扫描件要自己渲染**：腾讯云 `GeneralAccurateOCR` 官方注释明确「支持 PNG、JPG、JPEG、BMP」，
> 其 OCR 模块下不存在任何 Pdf Request 类（已核实 SDK 3.1.270 全量 class），故扫描件 PDF 必须先转图片。
> 20 页上限用于控制 OCR 按量计费成本。

**支持的文件类型（共 9 种，单文件 ≤ 20MB）**：

| 类别 | 扩展名 | 通道 |
|---|---|---|
| 文档 | `pdf` `docx` `txt` | 文字层（PDF 抽不到时降级 OCR） |
| 图片 | `png` `jpg` `jpeg` `bmp` `gif` `webp` | OCR |

> ⚠️ **不支持 `.doc`**（旧版 Word 二进制格式）。后端 `Constants.ALLOWED_DOC_EXTENSIONS` 只含
> `pdf/docx/txt` + 6 种图片，但 `DocToTextController` 的报错文案写了「仅支持 PDF/**DOC**/DOCX/TXT…」
> —— 传 `.doc` 会被拒，文案却暗示支持，属于已知不一致。

> ⚠️ **App 端（Android/iOS）当前选不了文件**：`tool-common.vue` 的 `onPureConvertPick` 分两条路 ——
> H5 用原生 `<input type="file">`，非 H5 用 `uni.chooseMessageFile`。
> 但按 uni-app 官方兼容表，**这两个 API 在 App 端都不支持**（`chooseMessageFile` 仅微信/QQ 小程序；
> `chooseFile` 仅 H5）。官方给 App 端的方案是插件市场「文件选择」或 `plus.io` 自绘选择框。
> 当前**图片也走同一个文件选择器**，所以在 App 端连图片都选不了。
> 若要补齐，图片类型可改走 `uni.chooseImage`（**全平台支持**，`image-bg-remover.vue` / `profile.vue` 已在用）。

### 提示词设计

加工层 4 个工具各 2 条系统提示词（`sys_ai_prompt` 的 `prompt_use` 区分），共 8 条：

| `prompt_use` | 作用 | 特点 |
|---|---|---|
| `format` | 约束**输出结构** | 规定章节、编号、表格、字数上限 |
| `generate` | 规定**角色与处理步骤** | 怎么读、提炼什么、什么不能编造 |

两者分离，用户可只改其中一项。`getDefaultByUse` 按 `id` 升序取第一条作为该工具的系统默认，
故迁移脚本统一「先逻辑删除旧的 → 再插入新的」（见 `sql/migrate_prompt_*.sql`，**这些脚本未入库**，仅本地保留）。

> **说明**：`bank-receipt-recognize` / `invoice-recognize` **不是工具**，它们仅作为**系统提示词**存在于 `sys_ai_prompt` 表（供 OCR 场景复用），不在 `sys_aitools_tool` 登记。

### 工具输入类型说明

`sys_aitools_tool.input_type` / `output_type` 为**逗号分隔的枚举集合**（`none`/`text`/`audio`/`document`/`image`/`file`），用于工作流节点连线时的类型匹配（`upstream.output_type ⊆ downstream.input_type`）。

### 用户自选 AI 模型

用户可在「我的 → 我的 AI 模型」里登记自己的 API Key，调用工具时从下拉框选模型，
不必用系统默认配置。

| 表 | 作用 |
|---|---|
| `sys_ai_model` | 系统模型注册表（模型编码、capability、是否内置），由 `v1_2026_10_12_ai_model_seeds.sql` 初始化 |
| `sys_user_ai_model` | 用户自填的 Key（密钥加密存储），由 `v1_2026_10_12_ai_model_upgrade.sql` 建表 |

**capability 机制**：模型按能力分类（`text` / `vision` / `ocr`），后端调用前校验所选模型
是否具备该工具所需 capability，不匹配直接拒绝，避免拿文本模型去做多模态活。
`modelCode` 会一路透传到实际调用。

| 接口 | 说明 |
|---|---|
| `GET /api/user-ai-model/list` | 我的模型列表 |
| `POST /api/user-ai-model/save` | 新增 / 更新 |
| `POST /api/user-ai-model/set-default/{id}` | 设为默认 |
| `DELETE /api/user-ai-model/{id}` | 删除 |

前端页面：`pages/my-models.vue`。

### 图片工具与本地 ONNX 模型

`image-bg-remover`（图片背景去除）**不调用云端 AI**，由后端 ONNX Runtime 做 CPU 离线推理。

**模型可插拔**：`segmentation.model` 决定用哪个分割模型，**换模型只改这一项** ——
输入边长、归一化 mean·std、resize 口径、输出语义会**自动跟着模型走**
（规格统一由 `common/SegmentationModelEnum` 提供，不在业务代码里手写）。

| model | 用途 | 输入 | 模型文件 | 体积 | 许可 |
|---|---|---|---|---|---|
| `modnet` | 人像 matting（默认） | 512² | `modnet_photographic_portrait_matting.onnx` | 25 MB | Apache-2.0 |
| `rmbg-1.4` | 通用显著物 | 1024² | `rmbg_1_4.onnx` | 168 MB | bria RAIL（**仅非商用**） |

放置目录：`code/aiTools_backend/src/main/resources/models/`

> ⚠️ **打包注意**：模型放在 `resources/` 下会**被打进 jar**（RMBG-1.4 168MB → jar 多 168MB）。
> 生产部署建议把模型**外挂**到包外，用 `segmentation.model-path` 指向绝对路径。

> ⚠️ **模型未入库**（`.gitignore` 已排除 `*.onnx`），clone 后该目录是空的，
> 图片工具启动即失败。获取方式见下。

#### ⚠️ 换模型时最容易踩的三个坑

1. **预处理口径**（mean/std、resize 方式）各模型不同，对不上**不会报错**，
   只会静默输出错蒙版 —— 所以口径统一收在枚举里，配套单元测试逐个锁死。
2. **输出语义**：不同模型的 ONNX 输出语义可能不同（0~1 概率 / 原始 logit）。
   对 logit 一律做 min-max 归一化，会把真实置信度 0.7% 的背景像素抬到 **35% 不透明度**
   → 抠出来是半透明鬼影。枚举里按模型区分 `PROBABILITY` / `LOGIT` 两种口径，
   新接模型时**必须先实测输出值域**再定口径。
3. **二值化阈值**：matting 类模型要保留软边（阈值 0），
   二值分割类模型要在 sigmoid 之后按 0.5 切一刀，否则背景那点低置信度 alpha 会连成鬼影。

#### 配置项

| 配置项 | 默认 | 说明 |
|---|---|---|
| `segmentation.model` | `modnet` | **默认**分割模型（见上表）。用户在工具页选了别的模型时以请求参数为准 |
| `segmentation.model-path` | 空 | 留空 = 按模型默认文件名取；填绝对路径 / `file:` 前缀 = **外挂模型**（热更模型不动包） |
| `segmentation.input-size` | `0` | 0 = 用模型自带的边长 |
| `segmentation.preprocess-mode` | 空 | 空 = 用模型官方口径；`keep-ratio-pad`（保持比例 + pad 127 中灰）/ `squash`（直接压成方形） |
| `segmentation.mask-threshold` | `0` | 0 = 不二值化；调小更激进（带出更多背景），调大更保守（边缘收缩） |
| `segmentation.feather-size` | `0` | 0 = 不羽化（MODNet 输出本身已连续，羽化反而模糊发丝） |
| `segmentation.min-input-size` | `640` | 小图先放大再送模型，保留发丝级细节 |
| `segmentation.max-image-pixels` | `40000000` | 解码**前**按像素总数拦超大图，避免 ARGB 缓冲把堆打爆 |

> 模型只接受**方形**输入（非方形会在内部 Concat 节点维度不匹配报错），
> 所以「保持长宽比」只能靠 **pad** 实现，不能直接送非方形。

#### 实测性能（16 GB / i7-12650H，纯 CPU 推理）

| 模型 | 单张耗时 | 常驻内存 |
|---|---|---|
| MODNet 512² | 0.4 ~ 0.5 s | 186 MB |
| RMBG-1.4 1024² | 1.3 s | 908 MB |

#### 模型从哪来

MODNet 官方仓库**不直接分发 `.onnx`**，只提供预训练权重（`.ckpt`）与推理代码，
ONNX 版由社区转换而来。三个可行来源：

1. **官方 Colab（ONNX 版，最省事）** —— 官方 README 列出的 ONNX Colab demo，
   在线跑完即可直接下载 `.onnx`：
   <https://colab.research.google.com/drive/1P3cWtg8fnmu9karZHYDAtmm1vj1rgA-f>
2. **社区 ONNX 转换脚本** —— 官方仓库 `onnx/` 目录（由 @manthan3C273 提供），
   拿官方 `.ckpt` 自行转换：
   <https://github.com/ZHKKKe/MODNet/tree/master/onnx>
3. **官方仓库** —— <https://github.com/ZHKKKe/MODNet>（Apache-2.0，含论文与 Colab 清单）

RMBG-1.4 的 ONNX 由 HF 官方镜像仓库直接提供：

- <https://huggingface.co/briaai/RMBG-1.4>（`onnx/model.onnx`，fp32 168MB）

> 国内网络访问 HF 慢时，用镜像 `https://hf-mirror.com/<同样的路径>` 即可。

#### 合成模式

`image-bg-remover` 一个端点承担 3 种模式，由请求参数 `mode` 决定：

| mode | 行为 | 输出 |
|---|---|---|
| `transparent` | 单纯去背景（默认） | 透明底 PNG |
| `color` | 换底色（红 / 蓝 / 白） | PNG |
| `image` | 换自定义背景图 | JPG |

抠图（ONNX 推理）环节三种模式完全共用，只有后处理合成策略不同。

**分割模型可在工具页切换**：`pages/image-bg-remover.vue` 上有模型选择器，
选中的模型随 `model` 参数传给 `POST /api/ai-office/image-bg-remover`；
不传则用 `segmentation.model` 配的默认模型。

> ⚠️ **不要用 `u2netp.onnx` 顶替。** U²-Net 用的是「整图 maxPix 做除数」那一套归一化，
> 与本项目枚举里的任何口径都不一致；喂错模型**不会报错**，只会静默输出错蒙版。
> 仓库里原来的 `u2netp.onnx` 已删除。

#### 换成外挂模型

`segmentation.model-path` 支持 `classpath:models/x.onnx`、绝对路径或 `file:` 前缀，
解析统一走 Spring `ResourceLoader`。部署时把模型外挂到包外、改配置即可**热更模型不动包**。

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

**相关表**：`sys_workflow`（工作流定义）、`sys_workflow_run`（运行历史）、`sys_tool_output`（节点产物）；DDL 均在 `code/aiTools_backend/sql/init.sql`（本地另有 `init_workflow.sql` 等拆分脚本，**未入库**）。

## 智能体模块（Agent）

用户只给一个**自然语言目标**，智能体（LLM）运行时自己决定调哪个工具、传什么参数、调几次，
直到产出答案。工具页入口在**首页 → 智能体**。

| 能力 | 说明 |
|---|---|
| 工具清单 | **从 `sys_aitools_tool` 动态生成**（description + input_type + output_type），不维护代码里的注册表 |
| 可用工具 | `audio-transcribe` / `doc-to-text` / `doc-keypoint-extract` / `ocr-recognize` / `work-summary` / `weekly-report` / `meeting-minutes`（image→image 与 none 类型的工具被排除：产物不是文本，喂不回上下文） |
| 动作协议 | 模型输出**文本 JSON**（`{"thought":..,"action":"tool_call","tool":..,"args":{..}}` / `{"action":"final","output":..}`），解析器带容错（代码围栏、前后废话、字符串里的花括号） |
| 执行循环 | ReAct 单循环，**四条硬约束**：步数 ≤ 8、解析失败只纠正 1 次、连续工具失败 2 次即停、工具结果回灌前截断 2000 字 |
| 附件 | 先 `POST /api/file/upload` 上传拿 URL，智能体只收 JSON（App 端 SSE 走 renderjs 层，带不了 multipart）；模型侧只见 `file_1`/`file_2` 这样的稳定 ID |
| BYOK | 支持用户自带模型 Key（`ModelCredentialResolver`）—— 智能体最烧 token，必须让用户能用自己的额度 |
| 运行记录 | `sys_agent_run` 表，`steps` 存每一步（想什么/调了什么/结果/耗时），前端「智能体记录」页可回看 |

| 接口 | 说明 |
|---|---|
| `POST /api/agent/run` | 跑一次智能体（SSE 流式）。入参 `{ goal, modelCode, files:[{name,url}] }` |
| `POST /api/agent/cancel` | 取消正在运行的智能体（入参 `{ runId }`，runId 由运行首帧下发） |
| `GET /api/agent/runs?limit=20` | 我最近的运行记录（不含步骤明细） |
| `GET /api/agent/runs/{runId}` | 运行详情（含步骤明细） |

**App 端可用性**（这三件事在 H5 端测不出来，单列出来）：

- 页面必须挂 `<sse-renderjs />`（`#ifdef APP-PLUS`）—— App 端 SSE 由视图层发起 XHR，
  页面不注册组件，请求会直接报「流式组件未就绪」
- 自动滚动用 `scroll-into-view` + 末尾锚点（`scroll-top` 设大值在 App 端不可靠）
- **App 端附件只能选图片**：`uni.chooseFile` / `chooseMessageFile` 在 App 端不被支持，
  App 端走 `uni.chooseImage`；文档 / 录音请在 H5 端上传

**错误码**（7xxxx 段「工作流 / 智能体编排」）：`70007` 格式错误 / `70008` 动作不完整 /
`70009` 工具连续失败 / `70010` 步数用尽 / `70011` 已取消 / `70012` 记录不存在 /
`70013` 所选模型不可用。

**SSE 帧约定**（与项目现有标记格式一致）：

```
--- [思考:xxx] ---          模型这一步在想什么
--- [工具:xxx 耗时 123ms] --- 开始调用某个工具
（普通 chunk）                工具结果正文
--- [工具结果结束:xxx] ---   工具结果结束
--- [答案] ---               之后的普通 chunk 是最终答案
--- [完成] ---               全部结束
```

> 智能体走**独立线程池**（`agentExecutor`，core 2 / max 4）：一次运行会占住一个线程几十秒到几分钟，
> 不能和「一问一答」的首字延迟敏感的 SSE 工具池抢线程。

设计文档见 `docs/agent-design-v2.md`（含现状勘测、流程评审与实施状态）。

## 其他模块

| 模块 | 接口前缀 | 说明 |
|---|---|---|
| 用户 | `/api/user` | 登录/注册/找回账号/改密/资料（JWT 鉴权） |
| 我的 AI 模型 | `/api/user-ai-model` | 用户自填 Key 的模型 CRUD + 设默认（见「用户自选 AI 模型」） |
| 历史记录 | `/api/history` | 工具调用历史（主表 + 明细 + 文件） |
| 提示词 | `/api/prompt` | 系统提示词查询 + 用户自定义提示词 CRUD + AI 生成 |
| 收藏 | `/api/favorite` | 工具/提示词收藏（`sys_user_favorite`，DDL 见 `sql/init.sql`） |
| 图片处理 | `/api/ai-office/image-bg-remover` | 人像分割（本地 ONNX 离线推理） |
| 智能体 | `/api/agent` | 目标 → LLM 自主选工具 → 答案（SSE） |
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
   - OpenCode Go API Key（大模型）
   - JWT Secret
3. **提交后**仍建议轮换一次——GitHub 即使删除 commit，历史中仍可恢复。
4. 腾讯云子账号请使用 **最小权限策略**（仅授权所需存储桶的读写）。

## 录音转写（腾讯云）

`audio-transcribe` 只有**腾讯云**一条转写通道：云 API `CreateRecTask`（录音文件识别）+ `DescribeTaskStatus` 轮询。
音频先经 ffmpeg 统一转成 16kHz 单声道 mp3，再以 `SourceType=1` 内联 base64 提交。

**调用方式**：

```bash
curl -X POST "http://localhost:8080/api/ai-office/meeting-minutes/transcribe?engine=tencent" \
     -H "Authorization: Bearer $TOKEN" -F "file=@audio.mp3"
```

> `engine` 参数仅为接口兼容保留（历史前端会传），当前后端忽略该值、固定走腾讯云。

**配置**（`.env`，`application-dev.yml` 的 `asr.tencent` 段读取）：

```
ASR_TENCENT_ENABLED=true        # 是否启用腾讯云通道（必须为 true）
ASR_APP_ID=                     # 腾讯云语音识别控制台 →「账户信息」（ASR 专用）
ASR_SECRET_ID=                  # 可选，留空自动复用 COS_SECRET_ID
ASR_SECRET_KEY=                 # 可选，留空自动复用 COS_SECRET_KEY
```

**未启用时的行为**：`enabled=false` 或缺少 `ASR_APP_ID` 时，接口直接返回中文错误
「语音识别服务未启用，请检查腾讯云 ASR 配置」，不再静默降级到其他通道。

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

## 批量上传的内存与并发

> 这一节解释「批量上传总大小 200MB」是怎么做到不把内存打爆的，
> 以及**换机器 / 换堆大小后该改哪个配置**。

### 问题的因果链

早期实现里，这三件事是绑在一起的：

```
Tomcat 请求结束就清理 multipart 临时文件
  → 异步跑批时读不到文件
  → 被迫在 Controller 里把每个文件 getBytes() 全读进内存
  → 10 个文件的字节同时驻留堆里
  → 只能把总大小限在 20MB（否则并发几个就 OOM）
```

**实测（10 个 20MB 文件）**：整批同时驻留 = **+207.9MB 堆占用**；
在 `-Xmx256m` 下跑 400MB 批量直接 `OutOfMemoryError: Java heap space`
—— 受害的是**整个后端进程**，其他用户的功能一起挂。

### 现在的做法：解开第一个环

1. **流式落盘**：Controller 不再 `getBytes()`，由 `BatchUploadService` 用 4KB 缓冲
   把文件复制到**我们自己的临时文件**（`java.io.tmpdir/aitools-batch-upload/`）。
   异步任务读自己的文件，**不再依赖 Tomcat 的临时文件**（Tomcat 请求结束就删自己的）。
2. **懒加载**：`payload.getContent()` 首次调用才读盘并缓存（同一文件只读一次）。
3. **用完即释放**：批量循环处理完一个文件就 `payload.release()`。

于是**峰值堆 = 并发批量数 × 单文件大小（20MB），与批量总大小（200MB）无关**。

实测对比（`-Xmx256m`，20 个 20MB 文件 = 400MB 批量）：

| | 旧方案 | 现方案 |
|---|---|---|
| 10×20MB 峰值堆 | +207.9 MB | **+32.2 MB** |
| 256MB 堆跑 400MB 批量 | **OutOfMemoryError** | **跑通，峰值 +30 MB** |

### 并发上限怎么算

限的是**批量线程池的最大线程数**（`batch.max-concurrent-tasks`，默认 8）——
不是队列长度。`ThreadPoolExecutor` 是「先入队、队满才扩线程」，
不限 `maxPoolSize` 的话高负载下会自动涨到 16。

单个重任务的峰值堆（2GB 堆口径）：

| 项 | 大小 |
|---|---|
| 单文件字节（`max-file-size` 20MB） | 20.0 MB |
| PDF 渲染单页 BufferedImage（150dpi A4） | 8.7 MB |
| PDF 累计 PNG（上限 20 页，按照片页 2.5MB 估） | 50.0 MB |
| 杂项（文本 / AI 缓冲 / JSON / 历史） | 5.0 MB |
| **单任务峰值** | **83.7 MB** |

预算：`2048MB 堆 − 800MB 预留`（GC 复制/晋升、元空间、线程栈、SSE 流式缓冲、
非批量请求、Tomcat 缓冲）`= 1248MB` → 理论上限 `1248 / 83.7 ≈ 14.9`
→ **取 2 倍安全系数 → 7~8 → 取 8**（8 × 83.7 = 670MB，占堆 32.7%）。

**换堆大小后重算**：

```
并发 = (堆 - 800MB) / 83.7MB / 2
```

改 `application.yml` 的 `batch.max-concurrent-tasks`，并用 `-Xmx` 指定堆。

### 相关的四处配置（必须同值）

| 位置 | 值 | 含义 |
|---|---|---|
| `application.yml` → `spring.servlet.multipart.max-file-size` | 20MB | 单文件上限 |
| `application.yml` → `spring.servlet.multipart.max-request-size` | 200MB | 整个上传请求上限 |
| `application.yml` → `server.tomcat.max-swallow-size` | 200MB | 请求被拒时 Tomcat 要读完请求体，否则连接被重置 |
| `Constants.BATCH_MAX_TOTAL_SIZE` | 200MB | 应用层总大小校验（multipart 之外再兜一层） |
| 前端 `config/tools.js` → `fileRule.maxTotalSize` / `maxFileSize` | 200MB / 20MB | 前端提前拦截，避免传完才被拒 |

> `server.tomcat.max-swallow-size` 最容易漏：Tomcat 默认只有 2MB，
> 请求被拒时它不读完请求体，**连接会被直接重置** ——
> 客户端看到 connection reset 而不是明确的 400 错误。

### 临时文件

- 位置：`java.io.tmpdir/aitools-batch-upload/`（文件名是 UUID，不用原始文件名）
- 清理：异步任务 `finally` 里删；线程池拒绝时单独删；进程被 kill 的残留
  由 `BatchTaskRecoveryJob` 定期扫（`batch.recovery.temp-file-retention-minutes`，默认 120 分钟）
- ⚠️ **磁盘占用**：批量线程池队列深度 50，理论上排队中的任务各自占着
  已落盘的文件（最坏 50 × 200MB ≈ 10GB）。实际占用取决于用户上传的文件大小，
  部署时留意临时目录所在卷的剩余空间。

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

### 3. ONNX 抠图模型不入库（`.gitignore` 已排除 `*.onnx`）—— ✅ 已处理

**现状**：`code/aiTools_backend/src/main/resources/models/` 下的模型**不入库**，
`.gitignore` 已加 `src/main/resources/models/*.onnx` 规则。

只需要 **`modnet_photographic_portrait_matting.onnx`（24.69 MB）** 一个；
原先同目录的 `u2netp.onnx`（4.36 MB）**已删除** —— 它与 `SegmentationServiceImpl`
的 MODNet 预处理不兼容，没有任何代码路径会加载它。
模型获取方式见「图片工具与本地 ONNX 模型」小节。

**影响**：clone 后 `resources/models/` 是**空目录**，必须先放入模型文件，
否则 `segmentation.enabled=true` 时图片工具（证件照换底 / 去背景）启动即失败。

**如需进一步自动化**（可选）：启动时检测模型缺失并从固定地址下载，
兼顾可复现与仓库体积，但需处理下载失败与文件校验。

**同批已修正**：`application-dev.yml.example` 里 `segmentation.model-path` / `input-size`
原为旧的 `u2netp.onnx` + `320`，已对齐代码实际的 MODNet + 512；
`SegmentationConfig` 的字段默认值与 javadoc、`pom.xml` 依赖注释同步更新。

### 4. App 端（Android/iOS）无法选择文件

**现象**：`tool-common.vue` 的文件选择分两条路 —— H5 用 `<input type="file">`，
非 H5 用 `uni.chooseMessageFile`。按 uni-app 官方兼容表
（[api/media/file](https://uniapp.dcloud.net.cn/api/media/file.html)），
**这两个 API 在 App 端都不支持**：`chooseMessageFile` 仅微信/QQ 小程序，`chooseFile` 仅 H5。

**影响**：文档提取、录音转写、批量文档工具在 **App 端完全无法上传文件**（图片也不行，
因为 `onPureConvertPick` 对图片和文档走的是同一个文件选择器）。这与项目
`AGENTS.md §1`「App 端适配为最高指令」冲突。

**修复方向**（按成本排序）：
1. **图片**：改走 `uni.chooseImage`（**全平台支持**，`image-bg-remover.vue` / `profile.vue` 已在用），
   改动最小，能立刻覆盖 doc-to-text 的图片场景
2. 文档：引入插件市场「文件选择」（Android 可用 Native.js 无需原生插件，iOS 需原生插件），
   或用 `plus.io` 自绘选择框读 `_doc` / `_downloads` / `_documents`
3. 短期兜底：在 App 端给出明确提示文案，而不是静默失败

### 5. ~~`.doc` 文件被拒但报错文案暗示支持~~ ✅ **已过期，无需改**

> 复核（2026-10-23）：后端文案已是「不支持的文件类型，仅支持 PDF/DOCX/TXT 或图片 PNG/JPG/JPEG/BMP/GIF/WEBP」，
> **不含 DOC**，与 `Constants.ALLOWED_DOC_EXTENSIONS` 一致。此条无需处理。

### 6. 交接报告遗留项的处置情况（2026-10-23 逐条核对）

| 遗留项 | 状态 |
|---|---|
| 静默失败规范（禁裸 catch / 禁 `getMessage()` 直给前端 / 禁错误码字面量） | ✅ 已落 `code/AGENTS.md` §12.7（含 5 条自检命令），现存违规已全部修掉 |
| 错误码覆盖收尾 | ✅ 前后端错误码一致性校验 **0 冲突** |
| 前后端错误码一码两义（`10001` 同时表示「用户不存在」和「图片格式不支持」） | ✅ 已修，四处对齐为 `30002` / `80002` |
| 统一文件选择组件 | ⏸ 未做（涉及多页面 UI + 需真机回归） |
| 历史文件落库（`sys_aitools_history_file` 恒空） | ⏸ 未做（需一整条「上传 COS + 写表 + 读时签发」链路，建议独立立项） |
| 状态快照（`userInfo.role` 不刷新） | ⏸ 未做（属产品行为决策）。后端登录响应**是**带 `role` 的，改角色后需重新登录 |
| 跨端测试矩阵（H5 / 小程序 / App） | ⏸ 需真机与小程序开发者工具，本环境无法完成 |

> 逐条明细见 `Desktop/项目问题报告/README.md` 的「交接报告里的遗留项」。
>
> 🔧 **本轮改动需要实机验证的部分 → `Desktop/项目问题报告/实机待测清单.md`**。

---

## 许可

---

## 2026-11-24 更新

### 工具页
- **顶栏统一**：返回箭头 + 标题 + 副标题（独立一行、与标题左对齐）+ 右侧历史记录图标
- **重点提取**补回输入区（此前模板缺分支，页面只剩模型选择）
- **格式要求 / 生成要求**改为从提示词库选择：
  - 系统预置 + 我的提示词一起展示，「系统预置 / 我的」双 tab + 搜索
  - 抽屉选择，选中后回填对应提示词字段；入口常驻一行，不会因提示词变多而拉长页面
  - 纯转换类工具（文档提取、录音转写）不显示这两块
- 副标题文案缩短，单行完整显示

### 首页
- 热门工具改**单行循环滚动**（内容复制两份做无缝衔接，按住暂停）
- 推荐卡改**轮播**：每 4 秒切换一个工具，含指示点，点击跳转对应工具

### 智能体
- **异常中断处理**：
  - 流正常结束但没收到 `[完成]` 帧 → 判定中断，保留半截回答并提示「回答中断，内容可能不完整」+ 重新生成
  - 网络失败 → 错误条带「重试」按钮
  - 用户主动取消 → 显示「已停止生成」，不再当错误
- **重新生成带附件**：新增 `regenerate` 参数，不新增 user 消息，附件从会话最后一条用户消息的 `meta.files` 还原
- 思考文本里的 `file_1` 自动还原为真实文件名
- 解析失败重试时补发「思考耗时」帧，修复界面出现两条「正在思考」
- 工具轮不再单独落思考步（历史里同一句思考不再出现两次）
- 启动时把遗留的「运行中」记录收尾为失败，避免永久转圈

### 管理端
- 新增**用户画像管理**页（仅 `role=admin` 可见）：
  - `GET  /api/admin/profiles` 查看所有用户的画像摘要与词条
  - `PUT  /api/admin/profiles/{userId}` 修改摘要 / 词条
  - 用户端不再展示画像入口

### 数据库
- 新增 `code/aiTools_backend/sql/2026-11-24_system_prompts.sql`：4 个加工工具的格式类 / 生成类系统预置提示词（可重复执行，不覆盖已有记录）
