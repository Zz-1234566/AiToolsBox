# 智汇工具箱 / AiToolsBox — 开发规范

> 本文档是项目级开发规范。下次在本项目做改动时必须遵守。
> 与本规范冲突的旧代码，**新写的部分**按本规范来；改老代码时按"最小修改"原则不要顺手重构。

---

## 1. 总体原则

- **【最高指令】所有改动必须以 App 端（Android / iOS）适配为最高优先级**。H5 是兼容性其次**。
  - App 端 `v-html` **不支持**，只能用 `<rich-text :nodes>` 节点数组
  - 任何"实时渲染 / markdown 实时预览 / 流式打字机"类功能，必须 **App 端单独写一套**（不能用 v-html）
  - H5 端可以用 v-html + marked；H5 改动 **不能反向影响** App 端行为
  - 跨端方案：用 `<!-- #ifdef H5 -->` / `<!-- #ifndef H5 -->` 分支，**两套独立渲染**
- **先定位 → 再分析 → 再修改 → 最后验证**。不猜需求、不猜业务、不猜实现。
- **最小修改**：能改一行不改两行；能复用不重写；能扩展不重写。
- **一次只改一个模块**，改完汇报本模块改动 / 影响范围 / 验证方式，等用户确认再做下一个。
- **未经用户授权**：不 commit / push / merge / rebase / 删分支 / 删代码。
- 公共字段、状态码、文案、阈值、开关 → 一律走 `common.Constants` / `common.*Enum`，**不要散落在 Controller / Service / 业务文件里手写**。

---

## 2. 分层结构（必须遵守）

```
Controller     → 只做：接收参数 / 调鉴权 / 调 service / 返回包装结果
Service (I)    → 接口定义（方法签名 + 状态码/常量 字面量）
Service (Impl) → 所有业务实现：状态机推进、文案选择、SSE 帧拼装、数据组装
Mapper         → MyBatis Plus BaseMapper
Entity / DTO / VO → 纯数据载体，不写业务
```

**禁止在 Controller 里写业务实现**。下面这些**典型反模式**：

- ❌ Controller 里 `if (s == STATUS_X || s == STATUS_Y) ... else ...` 判读
- ❌ Controller 里用 `switch (s)` 把 int 转中文
- ❌ Controller 里直接 `emitter.send(SseEmitter.event().data("--- [...] ---"))` 拼 SSE 帧
- ❌ Controller 里 `result.successCount == 0 ? FAILED : ...` 推终态
- ❌ Controller / Service 里出现硬编码阈值（10 / 200 / 1000 这种魔数）

**正确做法**：

- ✅ 状态码 → 中文 / 终态判断 → `BatchTaskStatusEnum.of(code).getLabel()` / `.isTerminal()`
- ✅ 阈值（最大文件数 / 总大小 / 超时秒数）→ `common.Constants.BATCH_MAX_FILE_COUNT` 等
- ✅ SSE 帧拼装 → 抽到 `BatchTaskService.subscribeProgress(task, emitter)` / `emitFinishedFrame(emitter, success, fail)`
- ✅ Controller 的 SSE 端点长这样：

```java
@GetMapping(value = "/xxx/stream/{id}", produces = "text/event-stream;charset=UTF-8")
public SseEmitter xxxStream(@PathVariable String id, HttpServletRequest req) {
    Long userId = authUtil.getUserIdFromRequest(req);
    XxxTask task = xxxService.getById(id);
    if (task == null) throw new BusinessException("xxx 不存在");
    if (!task.getUserId().equals(userId)) throw new BusinessException("无权访问");
    SseEmitter emitter = new SseEmitter(60_000L);
    executor.execute(() -> xxxService.subscribeProgress(task, emitter));
    return emitter;
}
```

> 注：`executor.execute(...)` 是允许的线程调度封装；**真正干活的代码必须在 service**。

---

## 3. 公共字段 / 常量 / 枚举 — 放哪里

| 类别 | 位置 | 例子 |
|---|---|---|
| 业务阈值（数字、字节数、超时） | `common.Constants` | `BATCH_MAX_FILE_COUNT = 10` / `BATCH_MAX_TOTAL_SIZE = 200MB` |
| 状态码 + 中文 label + 终态判断 | `common.*Enum`（每个状态机一个 enum） | `BatchTaskStatusEnum.of(code).getLabel()` / `.isTerminal()` |
| 接口常量（兼容旧调用） | `service.*Service` interface 内 `int X = Enum.Y.getCode();` | 让旧代码 `Service.STATUS_X` 不报错；新代码直接用 enum |
| 业务文案（SSE 帧头、错误消息） | service impl 内部 | 禁止在 Controller 拼 |

> Service interface 仍可保留 `STATUS_PENDING = BatchTaskStatusEnum.PENDING.getCode()` 这种引用，让旧调用编译过；
> **新代码优先用 enum，不要再用 interface 常量**。

---

## 4. service 严格 `interface + impl` 模式

- 每个 service 必须有 `service/XxxService.java`（interface）+ `service/impl/XxxServiceImpl.java`（@Service + @RequiredArgsConstructor）。
- **禁止直接写 public class 当 service**（之前 `OcrService` / `BatchTaskService` 拆过一次，新功能继续遵守）。
- Controller 通过 `private final XxxService xxxService;` 注入（@RequiredArgsConstructor 配合 Lombok）。

---

## 5. 工具编码 / 提示词

- `tool_code` 必须与前端 `src/config/tools.js` 的 key **逐字一致**。
- 用户能改的只有 `prompt_name`（对应 `sys_ai_prompt.prompt_name`）。
- 工具编码**不要散落成字符串字面量**：后端统一引用 `common/ToolCodes.java` 的常量
  （`ToolCodes.WORK_SUMMARY` 等），新增工具时在 ToolCodes 里加一条。

### 5.1 增删工具 → 必须同步的三处

| # | 位置 | 动作 |
|---|---|---|
| 1 | 前端 `src/config/tools.js` | 加 / 删 `TOOLS` 条目（含 `realized` 标记） |
| 2 | 后端 `common/ToolCodes.java` | 加 / 删工具编码常量 |
| 3 | `sql/init.sql` | **新建**工具 → 加 `INSERT INTO sys_aitools_tool`；**下线**工具 → 在 23.5 段的 `SET dr = 1 ... WHERE tool_code IN (...)` 里加编码 |

> ⚠️ 只改前端不改 SQL，新装环境会缺这行工具；
> 只改 SQL 不改前端，工作流节点下拉会多出空壳工具。
> **下线**走 `dr=1` 逻辑删除（保留回滚能力），不要 `DELETE` 行。

### 5.2 核对命令（改完工具后必跑）

对比「前端 key 集合」与「后端 dr=0 的 tool_code 集合」，应完全一致：

```bash
cd code
# 1) 前端权威清单（TOOLS 的 key）
grep -oE "^  '[a-z-]+': \{" aiTools_frontend/src/config/tools.js \
  | tr -d " ':{" | sort -u > /tmp/fe_tools.txt

# 2) 后端 init.sql 里所有被 INSERT 的 tool_code（只取 sys_aitools_tool 的 VALUES 首列）
awk '
  /INSERT INTO `?sys_aitools_tool`?/ { inblk=1; next }
  inblk && /^ON DUPLICATE KEY/ { inblk=0 }
  inblk && match($0, /'"'"'[a-z-]+'"'"'/) { print substr($0, RSTART+1, RLENGTH-2) }
' aiTools_backend/sql/init.sql | sort -u > /tmp/sql_ins.txt

# 3) 23.5 段被置 dr=1 的（下线）
sed -n '/23.5 删除冗余工具/,/AND `dr` = 0/p' aiTools_backend/sql/init.sql \
  | grep -oE "'[a-z-]+'" | tr -d "'" | sort -u > /tmp/sql_off.txt

# 4) dr=0 的 = INSERT 过的 - 已下线的
comm -23 /tmp/sql_ins.txt /tmp/sql_off.txt > /tmp/sql_live.txt

# 5) 逐条 diff（期望完全一致）
diff /tmp/sql_live.txt /tmp/fe_tools.txt && echo "✅ 一致"
```

> 已部署的旧库还要确认 23.5 段已执行过（否则旧库会多返回一批已下线的工具）。
> 查询：`SELECT tool_code, status, dr FROM sys_aitools_tool WHERE dr = 0;`

---

## 6. 配置文件 / 密钥

- 真实密钥、密码、Token → 走 `.env`（spring-dotenv 自动加载），**不入仓**。
- `.env.example` 入仓，里面是占位符。
- `application-dev.yml` / `application.yml` → 用 `${ENV_VAR:default}` 引用，**禁止写真值**。
- `.gitignore` 排除 `.env`、`.env.local`、`*.local`。

---

## 7. B2 多文件批量 AI 处理 — 端点分工

> ⚠️ **本节已于 2026-10-22 按实现修正**（原描述停留在已废弃的 SSE 方案）：
> ① `batch-upload` **返回普通 JSON（batchId），不是 SseEmitter**；
> ② 前端走**纯轮询** `/batch/{batchId}/completed`，**不消费任何 SSE 帧**；
> ③ `batch-stream` / `batch-status` 两个端点**前端零调用**，仅作历史兼容保留。

| 端点 | 方法 | 职责 | 前端是否在用 |
|---|---|---|---|
| `/document-summary/batch-upload` | POST multipart | **唯一入口**：建任务 → 立即返回 JSON `{batchId}` → 线程池异步跑 service → 跑完入库 | ✅ |
| `/ocr-recognize/batch-upload` | POST multipart | 同上（OCR） | ✅ |
| `/batch/{batchId}/completed` | GET | **增量轮询**（前端实际使用）：`since=N` 返回 since 之后新完成的 items，返回 BatchStatusVO（含 `results[]` + 计数） | ✅ |
| `/document-summary/batch-stream/{batchId}` | GET SSE | 补发 / 心跳（**前端零调用，历史兼容保留**） | ❌ |
| `/document-summary/batch-status/{batchId}` | GET | 轮询 / 断线重连（**前端零调用，历史兼容保留**） | ❌ |

前端轮询协议（`api/ai.js`、`api/image-bg-remover.js`）：

```
GET /api/ai-office/batch/{batchId}/completed?since=N
→ { status, processedIndex, results[], fileCount, successCount, failCount, ... }
→ 前端按 processedIndex 增量追加渲染；status 2=完成 3=部分失败 4=全部失败
```

> 历史遗留的 SSE 帧格式（`data: --- [任务已创建 batchId=xxx] ---` 一类）**已不再由前端消费**，
> 新增功能不要照此实现。

---

## 7.5 日志标记约定

日志前缀用于**按模块快速检索**，格式 `[模块名]`。

- **前缀一律在前**，同一模块的子分支用 `-` 派生：
  `[B2]`（批量通用）→ `[B2-DOC]` / `[B2-OCR]` / `[B2-FILE]` / `[B2-ASR]`
  ❌ `[OCR-B2]`（顺序相反，会让 grep 漏掉一半日志）/ ✅ `[B2-OCR]`
- 新增工具时**在既有家族下派生变体**（如 `[B2-XXX]`），不要自造顺序或自造家族。
- `[B2]` 单独使用时（工具无关的通用环节，如 `BatchTaskServiceImpl`），
  **日志正文必须带可区分上下文的信息**（toolCode / fileCount / batchId），否则排查时定位不到是哪个工具。
- 标记是**刻意保留的检索前缀**，做代码清理时**不要一并删除**。
- ⚠️ **改名有历史断层**：`[OCR-B2]` 已于 2026-10-23 统一为 `[B2-OCR]`，
  但**线上历史日志里仍是旧标记**。排查历史问题时**两个都要 grep**：
  ```bash
  grep -rnE "\[(OCR-B2|B2-OCR)\]" <日志文件>
  ```
- 其他家族：`[ASR-*]`（语音）、`[Compress]`（压缩）、`[Resolver]`（凭据）、`[HandlerFactory]`（工厂）。

---

## 7.6 批量上传的内存约束（硬性要求）

> 这条是**踩过坑才定下来的**：批量上传上限 200MB，而早期实现把每个文件的
> `MultipartFile.getBytes()` 全量读进堆、再把 10 个文件的 byte[] 一起装进 List ——
> 实测 10×20MB 同时驻留 = **+207.9MB 堆占用**；
> 在 `-Xmx256m` 下跑 400MB 批量直接 `OutOfMemoryError: Java heap space`。
> **受害的是整个后端进程**（不只是这一个请求），其他用户的功能一起挂。

### 规则

1. **禁止在 Controller 里对批量文件调 `getBytes()`**。
   批量文件必须经 `BatchUploadService.toPayloads(files)` **流式落盘**到临时目录
   （4KB 缓冲逐段拷，堆占用 ≈ 0），再由异步任务逐个读进内存处理。
2. **逐个文件处理 + 用完即释放**：批量循环里处理完一个文件必须调 `payload.release()`，
   否则 N 个文件的字节又会同时驻留（等于没做落盘）。
3. **临时文件必须清理**：异步任务的 `finally` 里调 `batchUploadService.cleanup(payloads)`；
   线程池拒绝（`RejectedExecutionException`）时 lambda 不会执行，要在 catch 里单独清。
   进程被 kill 的极端情况由 `BatchTaskRecoveryJob` 的兜底扫描清理。
4. **峰值堆 = 并发批量数 × 单文件大小（20MB）**，与批量总大小（200MB）无关。
   这才是「内存不爆」的根本保证 —— 限制总大小只是兜底，不解决并发。

### 上限的四处必须同步

| # | 位置 | 值 |
|---|---|---|
| 1 | `application.yml` → `spring.servlet.multipart.max-request-size` | 200MB |
| 2 | `Constants.BATCH_MAX_TOTAL_SIZE` | 200MB |
| 3 | 前端 `config/tools.js` → `fileRule.maxTotalSize` + 文案 | 200MB |
| 4 | `application.yml` → `server.tomcat.max-swallow-size` | 200MB |

> 第 4 项容易被漏：Tomcat 默认 `maxSwallowSize` 只有 2MB，请求被拒时
> Tomcat 不会把请求体读完，**连接会被直接重置** —— 客户端看到 connection reset
> 而不是明确的 400 错误。

> `max-file-size`（单文件 20MB）保持不变，与 `Constants.MAX_FILE_SIZE` 一致。

---

## 8. 工作区整洁（硬性要求）

**代码工作区只放「产品代码 + 必须入库的配置/文档」，其余一律不进。**

### 8.1 禁止放在代码目录的文件

| 类型 | 例子 | 正确位置 |
|---|---|---|
| **测试脚本** | `test_xxx.py`、`probe.cjs`、`check_xxx.mjs` | 测试完即删；需保留则放代码区外 |
| **测试数据** | `_test_audio/`、`test-data/`、临时音频/图片 | 同上 |
| **调研与报告** | 技术调研、评估报告、开题/论文材料 | 项目外独立目录 |
| **一次性脚本** | `_count.py`、`_extract2.py`、`db_query.py` | 用完即删 |
| **中间产物** | `out.txt`、`dump-*.ps1`、`prompts_full.txt` | 用完即删 |

### 8.2 外部产物目录

```
C:\Users\34170\Desktop\智汇工具箱-工作文档\
├── 开题报告-智汇工具箱.txt
├── 实时语音转写调研.md
├── 腾讯云ASR接入评估.md
└── test_asr.py
```

### 8.3 收尾检查（每次任务结束前必做）

1. 删掉本次产生的测试脚本与临时文件
2. `git status --short` 确认**无新增未跟踪文件**（历史遗留项不算）
3. 只允许看到本次业务代码的改动

### 8.4 历史遗留（尚未清理）

> 当前 `code/` 下**已无**未入库的调试脚本（`_check.cjs` / `_extract2.py` / `_count.py` /
> `db_query.py` / `_test_audio/` 等均已清理）。
> 后续若再产生临时脚本，仍按 §8.3 在任务结束前删掉 —— 本节留作提醒，不是待办清单。

```bash
# 自检：确认代码目录没有临时脚本残留
cd code
git status --short
# 期望：只看到本次业务代码的改动，无 ?? 开头的未跟踪临时文件
```

---

## 9. 字符串截断规范

任何「截断过长文本」的实现必须遵守（**已踩过坑**）：

```java
// ✅ 正确：先按 UTF-16 长度判定超限，再用 offsetByCodePoints 求安全的 char 下标
if (text.length() > max) {
    int end = text.offsetByCodePoints(0, Math.min(max, text.codePointCount(0, text.length())));
    text = text.substring(0, end);
}
```

| 坑 | 说明 |
|---|---|
| `length()` vs `codePointCount()` 口径不同 | emoji / 生僻字占 2 个 char。`length()=30000` 的 emoji 文本，第 20000 个 codepoint 的下标是 40000 → **越界抛 IndexOutOfBoundsException** |
| 阈值语义要写清 | `AI_OUTPUT_MAX_LENGTH` 是 **UTF-16 长度**（防 MySQL `text` 65535 **字节**溢出），不是 codepoint 数 |

**统一常量**（勿用魔法数字）：
- `Constants.AI_INPUT_MAX_LENGTH` = 20000（喂模型）
- `Constants.AI_OUTPUT_MAX_LENGTH` = 16000（落库）
- `Constants.TRUNCATE_SUFFIX`（截断标记，必须追加，否则用户不知道内容不完整）

**落库前必须截断**：`sys_aitools_history_detail` 的 `input_content` / `output_content` 是 MySQL `text`。

---

## 10. 改动流程

1. 改前先说：本模块改动 / 影响范围 / 验证方式
2. 用户确认后再动
3. 改完跑 `mvn compile` 验证（前端跑 vite build）
4. **不主动 commit / push** — 等用户测试通过
5. 测试有 bug → 整理问题 + 分析根因 → 提方案 → 等用户确认 → 再改

### 10.1 需真机验证的改动 → 登记到「真机校验报告」

**构建产物只能证明「代码被正确编译」，不能证明「真机行为正确」。**
凡是**浏览器 / 模拟器无法复现**的行为（安全区、软键盘、App 端逻辑层↔视图层、
canvas 合成、相册/系统权限、后端调用次数等），改完后必须：

1. 追加到 **`C:\Users\34170\Desktop\智汇工具箱-工作文档\待办\真机校验报告.md`**：
   - 第三节加一个小节（`H.` / `I.` …），写清「背景 → 步骤 → 期望 → 失败症状」；
   - 第四节加一行回填行；
   - 第六节补本轮改动清单。
2. 该报告是**真机验证单一台账**，所有需真机验证的项都往里加，**不要另起新文档**。
3. 报告位置在**项目外独立目录**（见 §8.1：报告不进代码仓），但内容要跟着代码走 ——
   提交信息里写明「本轮待验项见真机校验报告第 X 节」。

---

## 11. 沟通 / 反馈

- 中文回复，技术术语保留英文
- 简明扼要，不堆方案、不绕弯
- 较大改动 / 删代码 / 改 DB / 改公共接口 → **先沟通 + 给多方案** + 等用户确认

---

## 12. Service / Handler / Component 拆分规范（强制）

> 第 4 节已经规定了 service 必须 `interface + impl`，本节补充 **handler / 其他 Spring Bean** 的拆分要求，以及"为什么要拆"。

### 12.1 强制规则

| 类型 | 必须 interface + impl | 例外 |
|---|---|---|
| **Service** | ✅ **必须** | — |
| **Handler**（多态分派 / Factory 注入 `List<XxxHandler>`） | ✅ **必须** | — |
| **Strategy / Factory 选中的目标** | ✅ **必须** | — |
| **工具类 / 静态常量 / Spring 配置类** | ❌ 不需要 | `@Configuration` / `*Util` / `*Constants` / `*Enum` |
| **Controller** | ❌ **禁止**拆（接口契约 = URL + 请求体 + 响应）| 100% class |
| **Mapper（MyBatis）** | ❌ **禁止**改 class（`BaseMapper<T>` 要求 interface）| 100% interface |

### 12.2 强制要求 —— 类型 × 场景判断标准

| 场景 | 拆？ | 原因 |
|---|---|---|
| 注入到 Controller / Handler / 其他 Bean | ✅ **必须** | 调用方依赖接口，便于切换实现 |
| 单元测试要 mock | ✅ **必须** | Mockito 直接 mock interface，不依赖 Spring 容器 |
| Factory 模式分派（`List<XxxHandler>`） | ✅ **必须** | 注入类型必须为 interface，否则新实现必须改 Factory |
| 实现简单（< 50 行且无依赖） | 任意 | 仍建议拆分以保持一致 |

### 12.3 文件组织

```
service/
├── XxxService.java          ← interface
└── impl/
    └── XxxServiceImpl.java  ← class, @Service, @RequiredArgsConstructor

handler/
├── XxxHandler.java          ← interface
├── impl/
│   ├── AaaHandler.java      ← class, @Component("aaa")，implements XxxHandler
│   └── BbbHandler.java      ← class, @Component("bbb")，implements XxxHandler
└── HandlerFactory.java      ← class, @Component, 注入 List<XxxHandler>
```

### 12.4 反面案例（提交时必须驳回）

| 反模式 | 后果 |
|---|---|
| ❌ `public class TranscribeService` 直接 `@Service` | 单元测试无法 mock → 改逻辑要起整个 Spring 容器 |
| ❌ `public class JsonBatchHandler implements AiToolHandler` 直接 `@Component` | 后续要加新 Handler 必须改 Factory → 破坏开闭原则 |
| ❌ `public class TranscribeService` + `public class TranscribeServiceImpl`（**impl 也暴露 public**）| impl 应是包内可见或 `public`，但**调用方必须依赖 interface**（即 `@RequiredArgsConstructor` 注入 interface 引用） |
| ❌ Service interface 里写业务实现 | interface = 契约，全部 = 细节 |
| ❌ `@Component` 拆出"Service interface 的工具类"（如 `TranscribeHelper`) | 业务代码统一进 `*ServiceImpl`，不要发明新类 |

### 12.5 当前现状

| 位置 | 现状 | 状态 |
|---|---|---|
| `service/TranscribeService.java` | 已是 `interface` + `service/impl/TranscribeServiceImpl.java` | ✅ 已合规 |
| `handler/AiToolHandler.java` | 已是 `interface` | ✅ 已合规 |
| `handler/impl/{Json,SseText}{Batch,Single}Handler.java` | 4 个实现已放在 `handler/impl/` 下，`implements AiToolHandler` + `@Component("...")` | ✅ 已合规 |
| `handler/HandlerFactory.java` | 注入 `Map<String, AiToolHandler>`（Spring 自动按 type + bean name 收集），**架构已对**，是注册中心不是多态分派目标 | ✅ 无需拆 interface |
| `service/SegmentationBatchService.java` | `interface` + `impl/SegmentationBatchServiceImpl.java`（image-bg-remover 的批量编排，从 Controller 下沉） | ✅ 已合规 |

> ⚠️ 本表是**现状快照**，不是待办清单。改代码后请同步更新本表 ——
> 写着一份已经过时的「待修」表，比不写更危险（照着它做会做无用功）。

### 12.6 强制要求

新写的 service / handler / strategy 类 **必须按本规范**。提交时如不符合（class 直接 `@Service` / `@Component`），**不允许 merge**。

---

## 12.7 静默失败规范（强制）

> 来源：交接报告里反复出现的 P0 ——「已暴露 3 次『用户以为成功实际失败』」。
> 静默失败是最难查的一类 bug：**代码看起来正常，用户看到的是「成功」或「什么都没发生」**。

### 硬性规则

1. **禁止空的 catch 块**（`catch (Exception e) {}`）。
   至少要 `log` 出来；确实是有意的兜底（如「响应体不是合法 JSON 就用状态码文案」），
   **必须在 catch 里写注释说明为什么可以忽略**。
2. **`log` 必须带异常对象**：`log.warn("...: {}", e.getMessage(), e)` —— 最后的 `e` 不能省。
   只打 `getMessage()` 等于把根因丢了，排查时只能靠猜。
3. **禁止把 `e.getMessage()` 直接返给用户**：
   业务异常走 `ErrorFactory.of(ResultCode.X, "面向用户的文案")`，
   非预期异常走 `ResultCode.AI_TOOL_FAILED.getMessage()` 这类**固定的**用户文案。
   直接把 `getMessage()` 抛给前端 = 把内部异常细节（SQL、路径、类名）泄露出去。
4. **禁止用错误码字面量**（如 `new BusinessException(10001, ...)`）：
   必须用 `ResultCode.XXX.getCode()`。
   同一个数值在别处有别的含义时（`10001` = `USER_NOT_FOUND`），
   前端的 `errorCode.js` 兜底文案会显示成完全不相干的内容。
5. **前端同理**：`catch` 里不能只 `console.log` 就不管了 ——
   要么给用户 `uni.showToast`，要么注释说明为什么可以吞。

### 提交前自检

```bash
cd code
# ① 后端：空 catch 块
grep -rnE "catch *\([^)]*\) *\{ *\}" aiTools_backend/src/main/java/ --include=*.java
# 预期：无输出

# ② 后端：log 只打 getMessage()、丢了堆栈
#    （有意豁免的，在上一行注释里写「静默失败规范豁免：<原因>」，用 grep -v 排除）
grep -rnE "log\.(error|warn)\([^)]*getMessage\(\)\)" aiTools_backend/src/main/java/ --include=*.java \
  | grep -vE "getMessage\(\)[^)]*,\s*[a-z]+\)" \
  | grep -v "静默失败规范豁免"
# 预期：无输出（最后一参必须是异常对象）

# ②' 豁免标记本身必须写明理由（不能只写标记不写原因）
grep -rn "静默失败规范豁免" aiTools_backend/src/main/java/ --include=*.java \
  | grep -vE "豁免：.{4,}"
# 预期：无输出

# ③ 后端：错误码字面量（应一律用 ResultCode.X.getCode()）
grep -rnE "new BusinessException\(\s*[0-9]+" aiTools_backend/src/main/java/ --include=*.java
# 预期：无输出

# ④ 前端：空 catch
grep -rnE "catch *\([^)]*\) *\{ *\}" aiTools_frontend/src/ --include=*.vue --include=*.js
# 预期：无输出

# ⑤ 前后端错误码一致性（同名码数值必须相同，且不能一码两义）
python - <<'PY'
import io, re
b = io.open('aiTools_backend/src/main/java/com/example/aitools/common/ResultCode.java', encoding='utf-8').read()
bc = {int(m.group(2)): m.group(1) for m in re.finditer(r'([A-Z_]+)\((\d+),', b)}
f = io.open('aiTools_frontend/src/api/errorCode.js', encoding='utf-8').read()
fc = {m.group(1): int(m.group(2)) for m in re.finditer(r'^\s*([A-Z_]+):\s*(\d+),', f, re.M)}
bad = [f"{n}: 前端={v} 后端={bc.get(v)}" for n, v in fc.items() if n in bc and bc[n] != v]
bad += [f"数值 {v}: 前端={n} 后端={bc[v]}" for n, v in fc.items() if v in bc and bc[v] != n]
print("冲突:", bad if bad else "无")
PY
# 预期：冲突: 无
```

---

## 13. 前端规范（强制）

> 前 12 节基本只覆盖后端。以下条款全部来自**实测踩过的坑**，每条都给出可验证的检查动作。

### 13.1 页面导航（返回 / 跳转）

| # | 规则 | 原因（实测） |
|---|---|---|
| 1 | 返回**必须** `import { safeBack } from '@/utils/pageTransition'` 调 `safeBack(兜底页)` | H5 的 `navigateBack` 走 `history.back()`，**栈空时既不报错也不触发 `fail` 回调**。写 `navigateBack({fail:兜底})` 的兜底形同虚设，点返回完全没反应 |
| 2 | **禁止**手写 `getCurrentPages().length > 1 ? navigateBack : reLaunch` | 逻辑虽对，但属于重复实现，改一处忘一处就是下一个 bug |
| 3 | `navigateTo` **禁止**指向 `pages.json` 里 `tabBar.list` 中的页面 | tabBar 页只能 `switchTab`。App / 小程序端 `navigateTo` 到 tabBar 页**直接失败**，H5 端侥幸能跳但会多压一层栈 |
| 4 | 跳 tabBar 页统一走 `switchTab()`（`pageTransition.js` 已导出） | 同上 |

**提交前自检**（过滤注释行；路径过滤用 `/`，Windows 下 `\` 匹配不到）：
```bash
cd code/aiTools_frontend
# 规则 1：navigateBack 的真实调用只应出现在 utils/pageTransition.js（唯一出口）
grep -rn "navigateBack(" src/ --include=*.vue --include=*.js \
  | grep -v "^\s*\*" | grep -v "pageTransition.js"
# 预期：无输出

# 规则 3：navigateTo 不得指向 tabBar 页（index / category / workflow / favorites / my）
#         —— 这 5 个是 pages.json 的 tabBar.list，只能 switchTab
grep -rn "navigateTo" src/ --include=*.vue --include=*.js \
  | grep -v "^\s*\*" \
  | grep -E "/pages/(index|category|workflow|favorites|my)([?\"'\`]|/)"
# 预期：无输出
```

> 规则 3 的检查要按 **tabBar.list 的实际内容**更新，不要只照抄上面这 5 个路径 ——
> 增删 tabBar 页时同步改这条命令。

### 13.1.1 跨端文件选择（禁止自己写平台分支）

**选文件必须走 `utils/filePicker.js` 的 `pickFiles()`** —— 全项目**唯一**的选文件入口。

各端可用的 API 完全不同（实测，uni-app 官方文档 App 一列全是 ❌）：

| 能力 | H5 | 微信/QQ 小程序 | App（Android/iOS） |
|---|---|---|---|
| `<input type="file">` 原生多选 | ✅ | ❌ | ❌ |
| `uni.chooseImage`（图片） | ✅ | ✅ | ✅ |
| `uni.chooseMessageFile`（文档/音频） | ❌ | ✅ | ❌ |
| `uni.chooseFile`（文档） | ✅ | ❌ | ❌ |

**即：App 端目前只能选图片**，文档/音频在 App 端无解（要支持需引插件市场「文件选择」或用 `plus.io` 自绘）。

`pickFiles()` 统一处理：能力探测（`typeof` 判断，探测不到就返回 `reason:'unsupported'` 由调用方明确提示，**不静默失败**）、
返回值归一化（`{ fileName, file, path, size }`）、扩展名兜底过滤（H5 的 `accept` 只是浏览器提示，用户仍可选「所有文件」）。

```js
const res = await pickFiles({
  count: 10,
  accept: '.pdf,.docx,.txt',
  fileType: 'file',        // 'image' | 'audio' | 'file'
  sourceType: ['album', 'camera'],
  fileLabel: '文件'        // 用于拼提示文案
})
if (res.ok) { /* res.files */ }
else if (res.reason === 'unsupported') { /* 明确提示「当前平台暂不支持…」 */ }
else if (res.reason === 'empty') { /* 提示 res.message（扩展名不支持） */ }
// 'cancelled' = 用户主动取消，不提示
```

**提交前自检**：
```bash
cd code/aiTools_frontend
# 平台分支只应出现在 utils/filePicker.js
grep -rnE "uni\.choose(Image|MessageFile|File)\(" src/ --include=*.vue --include=*.js   | grep -v "filePicker[.]js"
# 预期：无输出
# ⚠️ 过滤用 "filePicker[.]js" 而不是 "utils/filePicker.js"：
#    Windows 下 grep 输出的路径分隔符是 \，按 / 过滤会漏掉。
```

### 13.1.2 本地存储 key（storage key）

**storage key 必须用 `utils/auth.js` 的 `STORAGE_KEYS`**，不要写字符串字面量。

storage key 是**跨模块共享契约**：`auth.js` 写、`request.js` 读、`auth-error-handler.js` 删、
`settings.vue` 清登录态。散落成字面量时，改一处漏一处就会出现
「写的是 `'Token'`、读的是 `'token'`」→ **登录态诡异失效，且极难排查**（要等到下次登录才发现）。

| 常量 | 值 | 用途 |
|---|---|---|
| `STORAGE_KEYS.TOKEN` | `'token'` | 登录 token（JWT） |
| `STORAGE_KEYS.USER_INFO` | `'userInfo'` | 登录用户信息（含 role） |
| `STORAGE_KEYS.PENDING_ACCOUNT` | `'pendingAccount'` | 忘记密码/注册流程暂存账号 |

**提交前自检**：
```bash
cd code/aiTools_frontend
grep -rnE "(get|set|remove)StorageSync\('(token|userInfo|pendingAccount)'\)" src/ --include=*.vue --include=*.js
# 预期：无输出
```

### 13.2 keep-alive 与页面状态（**最隐蔽的坑**）

`uni-h5` 用 `<KeepAlive>` 包 `RouterView`，缓存 key = `path + getStateId()`，**不含 query**。后果：

| 现象 | 说明 |
|---|---|
| 同路径不同 query 会**复用同一个实例** | `navigateTo('/tool-common?id=A')` → 返回 → `navigateTo('/tool-common?id=B')`，**`onLoad` 不再触发** |
| 页面实例持有的 `options` **不会同步更新** | 在 `onShow` 里读 `getCurrentPages().at(-1).options.id` 拿到的是**过期值**，比对逻辑永远成立 → 变成死代码 |

**规则**：

1. 凡是 `onLoad(option)` 里根据参数初始化状态的页面，**必须**额外处理参数变化复用。
2. ❌ **禁止**用 `onShow` + `getCurrentPages().options` 做这件事（拿不到新参数）。
3. ❌ **禁止**写 `computed(() => location.hash)` —— `location.hash` 不是 Vue 响应式源，computed 不会重算。
4. ✅ **正确做法**（见 `tool-common.vue` / `workflow-edit.vue` / `workflow-run.vue`）：
   ```js
   const routeHash = ref(location.hash || '')
   const onHashChange = () => { routeHash.value = location.hash || '' }
   window.addEventListener('hashchange', onHashChange)
   watch(routeHash, (h) => { /* 解析 query，与上次比对，不同则重新初始化 */ })
   ```
5. 页面级初始化统一抽成 `initPageState(option)`，由 `onLoad` 与 watch **共用**，避免两套逻辑漂移。
6. `onUnmounted` 里 `removeEventListener` 必须传**同一个函数引用**，否则解绑失败（泄漏）。

### 13.3 产物类型分流与下载（禁止重复实现）

后端产物分 5 类（`ToolOutput.outputType`）：`1文本 / 2图片 / 3视频 / 4音频 / 5文件`；
历史记录走的是「把产物 URL 写进 `output_content`」这条老路，只能靠扩展名反推。

**规则**：

1. 类型判定**只能**用 `utils/fileAction.js` 的 `outputKindOf()`，返回 `text|image|video|audio|file`。
   - ✅ 允许：`const kind = outputKindOf(x)` 之后 `kind === 'image'` 这类**对返回值的比较**；
   - ❌ 禁止：自己按扩展名 / 后缀重新判断一遍（`url.endsWith('.png')`、自建扩展名数组等）——
     那样等于把判定逻辑复制了一份，改动不会同步。
2. 产物展示：文本按文本渲染（Markdown），可视产物**内联展示**（图片 `<image>`、视频 `<video>`），**不得**把 COS 签名 URL 当正文铺出来。
3. 下载 / 打开**只能**用 `utils/fileAction.js` 的 `downloadOutput()` / `previewOutput()`，禁止再写 `uni.downloadFile` / `<a download>` 复制品。
4. 复制文本**只能**用 `utils/clipboard.js` 的 `copyRaw()` / `copyPlain()`。
5. `tool-common.vue` 的 `resultContent`、`batchCards` 等被写入的响应式数据，**必须**在模板中有渲染点 —— 曾出现过 12 处赋值、0 处渲染的缺陷。

**提交前自检**（过滤注释行）：
```bash
cd code/aiTools_frontend
# 下载实现只应出现在 utils/fileAction.js
grep -rn "createElement('a')\|uni\.downloadFile(\|saveImageToPhotosAlbum(" src/ \
  | grep -v "^\s*//" | grep -v "fileAction.js"
# 预期：仅 image-bg-remover.vue 的 canvas dataURL 特例
```

**唯一允许的例外**：`image-bg-remover.vue` 中下载 canvas 合成结果（`data:` URL）必须用 `<a download>`，
因为 `uni.downloadFile` 只接受网络 / 本地路径，不接受内存 data URL。

### 13.4 公共能力优先复用

写任何「复制 / 下载 / 类型判定 / 文件名解析 / 返回跳转」逻辑**之前**，先 grep `src/utils/` 确认是否已有现成实现。
新增公共能力必须落到 `src/utils/`，不得留在页面里。

`src/utils/` 现有职责：

| 文件 | 职责 |
|---|---|
| `pageTransition.js` | `safeBack` / `switchTab`，页面跳转唯一出口 |
| `fileDisplayName.js` | 文件名 / 扩展名 / COS object key 解析 |
| `fileAction.js` | 产物类型判定 + 下载 / 预览唯一出口 |
| `clipboard.js` | 复制文本（Markdown / 纯文本）唯一出口 |
| `auth.js` | 登录态校验、角色判断 |
| `workflowView.js` | 工作流节点图标 / 名称 / 展示计算 |

### 13.5 孤儿组件自检

新增或改动组件后，确认没有组件变成孤儿（组件文件存在但没有任何页面引用它）：

```bash
cd code/aiTools_frontend
for f in src/components/*.vue; do
  n=$(basename "$f" .vue)
  used=0
  # 逐个文件找引用，**跳过组件自身文件**（用 shell 的路径比较，不用 grep 输出里的路径）
  for g in $(find src/pages src/components -name '*.vue' -type f); do
    [ "$g" = "$f" ] && continue
    if grep -q "\b$n\b" "$g" 2>/dev/null; then used=1; break; fi
  done
  [ "$used" -eq 0 ] && echo "孤儿组件: $n"
done
```

> ⚠️ **写法要点**（三个坑都踩过）：
> 1. 不要用 `grep -rq`（静默模式）：无匹配时无输出 → 管道下游拿到空输入，
>    而 `grep -v` 对空输入退出码为 1 → **把全部在用组件都报成孤儿**。
> 2. **不要靠 `grep -v` 过滤 grep 输出里的路径前缀来排除组件自身文件** ——
>    Windows 下 grep 输出的路径分隔符是 `\`（`src\components\X.vue:12:...`），
>    用 `^src/components/` 这种正斜杠前缀**过滤不掉** → 组件自身的命中仍在，
>    `grep -q .` 恒成功 → **真孤儿永远检不出来**（静默失效，最难发现的一类）。
>    上面的写法用 shell 的 `[ "$g" = "$f" ]` 直接跳过自身文件，不依赖路径分隔符。
> 3. 按文件名**内容**排除（而不是按路径）会误伤别的页面里 import 这一组件的那一行。

> 判断以**实际目录**为准（`ls src/components/*.vue`），不要依赖任何写死的组件清单 ——
> 写死的清单会随代码演进而失效，比没有检查更危险。

### 13.6 设计 token（禁止写死样式值）

**`src/uni.scss` 是设计 token 的唯一定义处**（73 个变量，来源 `docs/ui-spec/01-设计系统.md`）。
`vite.config.js` 通过 `additionalData` 把 `uni.scss` **自动注入到每个组件的 scss 块**，
所以任何 `.vue` / `.scss` 里都能直接用这些 `$变量`，**不需要手动 @import**。

| 类别 | 变量示例 | 用法 |
|---|---|---|
| 间距 | `$spacing-1..$spacing-10`（8rpx 起，步长 8rpx） | `padding: $spacing-4;` |
| 圆角 | `$radius-sm/md/lg/xl/pill` | `border-radius: $radius-md;` |
| 字号 | `$font-size-xs/sm/...` | `font-size: $font-size-sm;` |
| 颜色 | `$brand-primary` / `$text-primary` / `$border-color` / `$color-success` ... | `color: $text-secondary;` |
| 渐变 | `$gradient-brand/doc/image/dev/audio/video/ocr/text/code` | `background: $gradient-image;` |

**规则**：

1. 新写样式**一律用 token**，不要写 `24rpx` / `#4B5563` / `linear-gradient(135deg, ...)` 这类字面量。
2. 颜色 token 形如 `$brand-primary: var(--brand-primary, #3B82F6)` ——
   **CSS 变量那一层是主题切换用的**，`utils/theme.js` 只在运行时改写
   `--bg-*` / `--text-*` / `--border-color` / `--divider-color` / `--color-*` 这几组。
   换成 token 意味着「跟随主题」，与写死不等价 —— 改之前先确认是不是想要这个效果。
3. 需要**跟随暗色主题**时用 `$text-*` / `$bg-*` / `$border-color`；
   需要**固定不随主题变**时（例如渐变头像）用 `$gradient-*` / `$brand-*`。
4. 已有页面里的写死值**不必一次性全量替换**（改动面大、回归成本高）——
   **新写的代码用 token**，改到哪个页面顺手把那个页面收敛掉即可。

**自检**（新增样式里是否还有裸 rpx / 裸色值）：
```bash
cd code/aiTools_frontend
# 找出还在写死间距的页面（示例：只查 padding/margin/gap 的裸 rpx）
grep -rnE "(padding|margin|gap):[^;]*[0-9]+rpx" src/pages src/components --include=*.vue \
  | grep -v "^\s*\*" | grep -vE '\$[a-z-]+'
# 输出较多属正常（存量未收敛）；**本次新增的样式不应出现在结果里**
```

