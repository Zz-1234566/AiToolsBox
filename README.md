# 智汇工具箱（AIToolsBox）

> 毕业设计项目：基于 uni-app + Spring Boot 的多端 AI 工具聚合应用

## 项目简介

一个面向移动端 / H5 的 AI 工具箱 App，内置工作总结、文档重点提取等 AI 办公能力，并预留了图片处理、效率小工具的扩展位。AI 调用走 DeepSeek（OpenAI 兼容协议），结果支持 SSE 流式打字机效果。

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

脚本会创建 `ai_toolbox` 库及全部业务表，并写入 2 个初始化工具（`work-summary`、`ai-summary`）及对应系统提示词。

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
| `AI_DEEPSEEK_API_KEY` | ✅ | DeepSeek（OpenAI 兼容）API Key | DeepSeek / 中转服务控制台 |

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
- `ai.deepseek.api-url: https://opencode.ai/zen/go/v1/chat/completions`
- `ai.deepseek.model: deepseek-v4-flash`

## 已实现的 AI 工具

| 工具编码 | 名称 | 输入 | 接口 |
|---|---|---|---|
| `work-summary` | 工作总结 | 文本 | `POST /api/ai-office/work-summary`（同步）<br>`POST /api/ai-office/work-summary/stream`（SSE 流式） |
| `ai-summary` | 文档重点提取 | 文档（PDF/Word/TXT） | `POST /api/ai-office/document-summary/stream`（SSE 流式 + multipart 上传） |

首页其余工具（周报生成、会议纪要、智能识别、图片类、效率小工具）**前端预留了 UI，后端未实现**，点击会提示"该工具开发中"。

## ⚠️ 密钥安全提醒

1. **提交前请确认 `application-dev.yml` 没有出现在 `git status` 中**——它已被 `.gitignore` 排除，但仍要复核。
2. **若曾在其他平台 / 旧仓库提交过任何含真实密钥的文件，请立即轮换**：
   - QQ 邮箱授权码
   - 腾讯云 COS SecretId / SecretKey
   - DeepSeek API Key
   - JWT Secret
3. **提交后**仍建议轮换一次——GitHub 即使删除 commit，历史中仍可恢复。
4. 腾讯云子账号请使用 **最小权限策略**（仅授权所需存储桶的读写）。

## 许可

