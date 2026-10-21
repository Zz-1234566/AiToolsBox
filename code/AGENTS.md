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

- `tool_code` 必须跟前端 `src/config/tools.js` 1:1。
- 用户能改的只有 `prompt_name`（对应 `sys_ai_prompt.prompt_name`）。
- 增删工具 → 同步 `tools.js` + `sys_aitools_tool` + `sys_ai_prompt` 三处。

---

## 6. 配置文件 / 密钥

- 真实密钥、密码、Token → 走 `.env`（spring-dotenv 自动加载），**不入仓**。
- `.env.example` 入仓，里面是占位符。
- `application-dev.yml` / `application.yml` → 用 `${ENV_VAR:default}` 引用，**禁止写真值**。
- `.gitignore` 排除 `.env`、`.env.local`、`*.local`。

---

## 7. B2 多文件批量 AI 处理 — 端点分工

| 端点 | 方法 | 职责 |
|---|---|---|
| `/document-summary/batch-upload` | POST multipart | **唯一入口**：建任务 → 立即返回 SseEmitter → 线程池异步跑 service → 跑完入库 → 推完成帧 |
| `/document-summary/batch-stream/{batchId}` | GET SSE | **补发 / 心跳**：任务已完成则补发 result_summary；进行中则提示改用 upload 订阅或 batch-status 轮询 |
| `/document-summary/batch-status/{batchId}` | GET | 轮询 / 断线重连：返回 BatchStatusVO（code + label + 计数） |
| `/batch/{batchId}/completed` | GET | **增量轮询**：since=N 返回 since 之后新完成的 items，前端每 1-2s 拉一次。返回 BatchStatusVO（含 items 数组） |

SSE 数据帧约定（前端按 --- 标记分块渲染）：

```
data: --- [任务已创建 batchId=xxx，开始处理] ---\n
data: --- [1/N: 文件名] ---\n
data: AI chunk...
data: \n--- [1/N 完成，耗时 Xms] ---\n\n
data: --- [1/N 失败: 原因] ---\n\n
...
data: --- [已入库，状态：xxx] ---\n
```

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

`code/` 下 `_check.cjs`、`_extract2.py`、`_count.py`、`db_query.py`、`_test_audio/` 等
是早期调试遗留，**未入库但污染工作区**，待用户确认后统一清理或移入外部目录。

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
5. 测试有 bug → 整理问题 + 分析根因 + 提方案 → 等用户确认 → 再改

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

### 12.5 当前现状（待修）

| 位置 | 现状 | 应改 | 工作量 |
|---|---|---|---|
| `service/TranscribeService.java` | ❌ class，`@Service` 直接写业务 | 拆 `TranscribeService`（interface）+ `TranscribeServiceImpl`（class）| 10 分钟 |
| `handler/AiToolHandler.java` | ✅ 已是 interface | — | — |
| `handler/JsonBatchHandler` 等 4 个实现 | ⚠️ 已是 `implements AiToolHandler` + `@Component("...")`，但**目录风格**未走 `handler/impl/` | 把 4 个文件从 `handler/` 根目录移到 `handler/impl/`（仅目录整洁，**不影响可扩展性**，可与拆 TranscribeService 一并做或延后）| 5 分钟 |
| `handler/HandlerFactory.java` | ✅ 注入 `Map<String, AiToolHandler>`（Spring 自动按 type + bean name 收集），**架构已对**，是注册中心不是多态分派目标 | 无需拆 interface | — |

### 12.6 强制要求

新写的 service / handler / strategy 类 **必须按本规范**。提交时如不符合（class 直接 `@Service` / `@Component`），**不允许 merge**。

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
# navigateBack 的真实调用只应出现在 utils/pageTransition.js（唯一出口）
grep -rn "navigateBack(" src/ --include=*.vue --include=*.js \
  | grep -v "^\s*\*" | grep -v "pageTransition.js"
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

新增或改动组件后，确认没有组件变成孤儿：

```bash
cd code/aiTools_frontend
for f in src/components/*.vue; do
  n=$(basename $f .vue)
  grep -rq "$n" src/pages src/components --include=*.vue | grep -v "components/$n.vue" || echo "孤儿组件: $n"
done
```

> 历史遗留孤儿（暂未清理，非本次引入）：
> `AudioInputArea` / `FileInputArea` / `InputSwitcher` / `PromptInputArea` / `TextInputArea` / `ToolCard`
