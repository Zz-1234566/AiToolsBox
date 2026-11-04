# 智能体（Agent）设计 v2 —— 现状勘测 + 重新设计

> 状态：**待评审**。本文只做设计，不含实现。
> v1（`agent-design-v1.md`）作废 —— 那版是在没做勘测的情况下写的，勘测后发现两处判断错了（见 §六）。
>
> **目标对齐**：沿用原 agent 设计稿（`代码与设计稿/agent设计稿（完善版）.md` V2.0 现状审计 /
> `agent-design-v3-errora.md` V3.0 勘误 / `impl-steps/` 7 份 / `agent-design-v4-roadmap.md` 路线图）
> 的**目标**，但实现按当前代码重新勘测后重做。
> 那套原稿在当前工作副本与 git 历史中均不存在（未提交），**目标由文件名结构与交接待办反推**（见 §二），
> **请确认这个反推对不对**。

---

## 一、现状勘测（2026-10-27 实读代码）

### 1.1 AI 能力层 —— 智能体的「大脑」

| 组件 | 能力 | 对智能体的含义 |
|---|---|---|
| `ai/AiClient` | OpenAI 兼容文本：`chat` / `chatStream`，BYOK 覆盖 key/url/model | **只支持 system+user 两条消息**，没有 messages 数组重载 → 智能体要的多轮必须自己拼 |
| `ai/MinimaxClient` | 多模态：`chatImage` / `chatImages` / `chatAudio` / `chatText` | 视觉/音频理解能力已有，但**只出不进**，不能当工具 |
| `ai/TencentAsrClient` | 腾讯云 ASR | 录音转写已有 |
| `ai/ResolvedCredential` | BYOK 凭据 | 用户可自带模型 Key —— 智能体最该支持这个（烧 token 最凶） |

> ⚠️ **关键缺口**：`AiClient` **没有原生 function calling**（请求体只有 `model/stream/thinking/messages`），
> 也没有 `tools` 参数。MiniMax 的 OpenAI 兼容接口本身是支持的，但项目**没有暴露这个能力**。

### 1.2 工具层 —— 智能体的「手脚」

`sys_aitools_tool` 登记了 **15 个工具**，且**已经带 `input_type` / `output_type`**：

| 能力形态 | 工具 |
|---|---|
| text → text | `work-summary`、`weekly-report`、`meeting-minutes`、`todo-list` |
| file → text | `doc-to-text`、`doc-keypoint-extract` |
| audio → text | `audio-transcribe` |
| image → text | `ocr-recognize`、`bank-receipt-recognize`、`invoice-recognize` |
| image → image | `image-bg-remover`、`portrait-bg-replace`、`image-compress` |
| text → image | `qr-code-gen` |
| none → text/none | `pomodoro`、`password-gen` |

**真实实现只有 4 个 Handler**（`handler/impl/`）：`json-single` / `json-batch` /
`sse-text-single` / `sse-text-batch` —— 工具的落地方式是「**按 `input_type` 分派到某个能力**」，
而不是每个工具一套实现。

`workflow/engine/NodeExecutor.executeOne()` 把这个分派固化成了 switch：

```
audio    → transcribeService.transcribe()
document/file → documentParser.parse()
image    → ocrService.recognizeText()
text     → aiClient.chat(格式提示词, 生成提示词 + 原文)
none     → 报错（不能作为节点执行）
```

> 💡 **这是智能体最值钱的复用点**：工具的「吃什么、产出什么」已经用 `input_type`/`output_type`
> 声明好了，**LLM 需要的工具说明表 = description + input_type + output_type，DB 里现成**。

### 1.3 工作流引擎 —— 智能体的「兄弟」

`workflow/` 共 2706 行：

| 组件 | 行数 | 能力 | 智能体用不用 |
|---|---|---|---|
| `WorkflowEngine` | 610 | 拓扑分层 + 同层并行 + 数组语义 + 失败传播 + 状态机 | ❌ 不复用（DAG ≠ 循环） |
| `NodeExecutor` | 428 | 工具执行 + 提示词解析 + 写历史 | ✅ **直接复用** |
| `WorkflowValidator` | 139 | 连线类型校验（`NodeIoTypeEnum.canConnect`） | ✅ 复用枚举 |
| `WorkflowServiceImpl` | 904 | 编排 CRUD + 运行 + 断点续跑 + 节点重跑 | 🔶 参考（运行记录的写法） |
| `WorkflowController` | 157 | 编排 CRUD + 运行 + 状态查询 | 🔶 参考 |

### 1.4 数据层

`sys_aitools_tool` / `sys_ai_prompt`（系统提示词，format+generate 两段式）/ `sys_ai_user_prompt`（用户自选提示词）/
`sys_workflow` / `sys_workflow_run` / `sys_tool_output` / `sys_aitools_history(_file)`。

**没有 agent 相关表**。

### 1.5 前端

25 个页面；关键基建：`utils/sseBridge.js`（跨端 SSE）、`components/MarkdownView.vue`（App 端走 rich-text）、
`utils/markdown/*`、`components/AiModelSelector.vue`（BYOK 选模型）。

### 1.6 与智能体直接相关的已知遗留问题

| 来源 | 问题 | 对智能体的影响 |
|---|---|---|
| 交接 §7.1 | `HistoryService#recordWithFiles` **零调用方** → 文件传完即丢，`history_file` 表恒空 | 智能体要把「附件」喂给工具，会踩同一个坑 |
| 交接 §7.4 | **历史回放没有逐帧日志**，SSE 帧不落库，只存终态 | 智能体的每一步天然就是逐帧的 → 智能体模块应当把 steps 落库（**但不要顺手改工作流**，那是另一个范围） |
| 交接 §7.2 | 老数据文件名是 UUID，永久不可恢复 | 与智能体无关，但新链路不要重复这个错 |

---

## 二、原目标的反推（**请确认**）

原稿不在仓库里，只能从 4 个文件名 + 交接待办反推：

| 文件名 | 推断 |
|---|---|
| `agent设计稿（完善版）.md` —— **V2.0 现状审计** | 设计从「审计现状」起步，不是凭空画饼 |
| `agent-design-v3-errora.md` —— **V3.0 勘误** | 有过一轮返工修正 |
| `impl-steps/` —— **7 份实施稿（2962 行）** | 被拆成 7 个实施单元 → **是个大功能**，不是加个聊天框 |
| `agent-design-v4-roadmap.md` —— **路线图** | 有分期 |
| 交接待办：「`impl-steps/` **第 1 批**实施（**5-6 周**）」 | 分批实施，且第一批就是个大工程 |

### 反推出的原目标

> **把项目从「工具箱 + 用户手工编排的工作流」升级为「智能体」** ——
> 用户只给一个自然语言目标，AI 自己规划步骤、挑选工具、组织参数、产出结果。
> 这是毕业设计的**收官级模块**（7 份实施稿 + 首批 5-6 周的分量）。

**如果这个反推不对，请纠正我** —— 目标不同，设计会差很远。

---

## 三、设计

### 3.1 与工作流的关系

| | 工作流 | 智能体 |
|---|---|---|
| 谁决定流程 | **用户**（可视化编排 DAG） | **LLM**（运行时决策） |
| 流程形状 | DAG，拓扑分层 | 循环（ReAct） |
| 复用 | — | `NodeExecutor` + 提示词体系 + 历史 + 产物 |

两者**互补不互斥**：工作流是「我已知要做什么，请按序执行」；智能体是「我只知道目标，你自己想办法」。

### 3.2 决策 1：动作协议用「文本 JSON」，不用原生 function calling

**理由**：

1. `AiClient` 当前**没有 `tools` 参数**，接原生 function calling 要改请求体 + 解析
   `tool_calls` + 回灌 `role=tool` 消息 —— 改动面比文本协议大；
2. 项目的模型可被 BYOK 换成任意 OpenAI 兼容服务，function calling 支持度参差；
3. 文本协议的解析器完全在我们手里 —— 模型不按格式输出时能报**可诊断**的错误，而不是 400。

**协议**（system 提示词要求模型**只输出一个 JSON 对象**）：

```json
{"thought":"用户要会议纪要，先把录音转成文字","action":"tool_call","tool":"audio-transcribe","args":{"file":"录音文件"}}
{"thought":"已拿到文字，生成纪要","action":"final","output":"会议纪要正文……"}
```

**解析容错**：从返回文本提取第一个 `{...}` 块 → 解析失败带错误说明重试 1 次 → 再失败终止并报「模型未按协议输出」。

### 3.3 决策 2：工具清单**从 DB 生成**，不写注册表

**这是勘测带来的最大简化**（v1 判断错了）：`sys_aitools_tool` 已经有 `description` +
`input_type` + `output_type` —— 这三样拼起来就是 LLM 需要的工具说明表：

```
- audio-transcribe（录音转写）：输入 audio，产出 text。把录音转写成文字。args: {"file": "录音文件名"}
- meeting-minutes（会议纪要）：输入 text，产出 text。把会议记录整理成纪要。args: {"text": "会议记录"}
...
```

**初版只暴露 text-in/text-out 与 file/audio/image-in/text-out 的工具**：

| 排除 | 原因 |
|---|---|
| `image-bg-remover` / `portrait-bg-replace` / `image-compress` | image → **image**，产物是文件不是文本，喂不回上下文；且抠图已经是独立工具页 |
| `qr-code-gen` | text → image，同上 |
| `pomodoro` / `password-gen` / `todo-list` | `none` 输入，纯交互或与 AI 无关 |

→ **初版 8 个工具**：`audio-transcribe`、`doc-to-text`、`doc-keypoint-extract`、
`ocr-recognize`、`work-summary`、`weekly-report`、`meeting-minutes` + （可选）`todo-list`

### 3.4 决策 3：工具调用复用 `NodeExecutor`，agent 只做薄适配

`NodeExecutor.execute(toolCode, inputs, params, userId, runId, nodeId, nodeName)` 已经是
「工具 code + 输入 + 参数 → 输出 + 写历史」的完整实现。

Agent 侧只需把 LLM 的 `args` 映射成 `(inputs, params)`：

| 工具 input_type | LLM 的 args | 映射到 NodeExecutor |
|---|---|---|
| `text` | `{"text": "..."}` | `inputs = [args.text]`，`params` 用该工具的系统提示词 |
| `file` | `{"file": "xxx.pdf"}` | `inputs = [已上传文件的本地路径]`（附件由前端先传上来） |
| `audio` | `{"file": "xxx.mp3"}` | 同上 |
| `image` | `{"file": "xxx.jpg"}` | 同上 |

> `params` 里的 `promptFormat` / `promptGenerate` **由 `NodeExecutor.resolveNodePrompt` 自动兜底到
> 该工具的系统默认提示词**（`sys_ai_prompt`），用户选了提示词才覆盖 —— 初版 agent 不让用户选，
> 直接用系统默认 ✓ 零额外配置。

### 3.5 决策 4：ReAct 单循环 + 硬上限

```
目标 → LLM
   ├─ action=final     → 结束（成功）
   └─ action=tool_call → 执行工具 → 结果回灌 → 继续
```

**终止条件**（任一满足即停）：

| 条件 | 默认值 |
|---|---|
| LLM 返回 `final` | — |
| 步数上限 `maxSteps` | 8 |
| 连续工具失败次数 | 2 |
| 工具参数非法 → 回灌错误让模型自纠 | 1 次 |
| 用户取消（SSE 断开） | — |

### 3.6 决策 5：上下文控制

每轮 messages：

```
system:    智能体人设 + 协议说明 + 工具清单（DB 生成）
user:      目标
assistant: 上一轮的 action JSON
user:      [工具结果] tool=xxx 成功/失败 结果=…
```

- 工具结果**截断到 2000 字符**（`Constants.AI_INPUT_MAX_LENGTH` 同款思路），超长给摘要 + 提示
- 步数上限 8 → 上下文天然有界

> `AiClient` 需要新增一个 **messages 数组重载** `chatMessages(List<Map<String,String>> messages, ResolvedCredential cred)`
> —— 现有 `chat(system, user)` 内部就是拼两条 message，把它拆出来复用即可，改动很小。

### 3.7 决策 6：数据模型 —— 单表，步骤存 JSON

与 `sys_workflow_run.node_results` **完全同构**（项目既有风格 ✓）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `run_id` | VARCHAR(64) | UUID，对外 id |
| `user_id` | BIGINT | 归属用户 |
| `goal` | TEXT | 用户目标 |
| `status` | TINYINT | 0待运行 1运行中 2成功 3失败 4已取消 |
| `steps` | MEDIUMTEXT | JSON：`[{seq,thought,tool,args,output,outputTruncated,costMs,errorMsg}]` |
| `final_output` | MEDIUMTEXT | 最终答案 |
| `step_count` | INT | 实际步数 |
| `duration` | INT | 总耗时 ms |
| `error_msg` | VARCHAR(500) | 整体错误 |
| `create_time` / `finished_at` / `dr` | | 与现有表一致 |

> 附带收益：智能体的 steps **就是逐帧日志**，
> 正好回应交接 §7.4 那个「历史回放没有逐帧日志」的痛点（但**只修智能体这一条链路**，
> 工作流的逐帧日志另开一轮，不在本次范围）。

### 3.8 决策 7：BYOK 必须支持

智能体是最烧 token 的功能，用户必须能用**自己的模型 Key**。
复用现有 `ResolvedCredential` + `AiModelSelector.vue` 的解析链路（`SysUserAiModelController` / `AiModelService`）。

### 3.9 接口

**`POST /api/agent/run`（SSE 流式）**

- 入参：`goal`（必填）、`files`（可选附件）、`modelCode`（可选，BYOK）
- 出参（SSE 帧）：

```
--- [STEP] 正在转写录音… ---
--- [TOOL] audio-transcribe 耗时 1.2s ---
--- [RESULT] …（工具结果，可截断）---
--- [ANSWER] …（最终答案，逐字）---
--- [DONE] ---      /   --- [ERROR:xx] ---
```

- 同时写 `sys_agent_run`（成功 / 失败 / 取消都落）

**历史**（第 2 批）
- `GET /api/agent/runs` —— 列表
- `GET /api/agent/runs/{runId}` —— 详情（含 steps）

### 3.10 前端

`pages/agent.vue`（新页）：

- 目标输入框 + 附件上传
- 过程流：每步一张卡片（工具名 + 参数 + 耗时 + 结果可折叠）
- 最终答案用 `components/MarkdownView.vue`
  > ⚠️ **App 端不能用 `v-html`**（AGENTS.md §1 硬要求）—— 复用 MarkdownView 现有的 rich-text 分支
- 入口：首页工具区加一张「智能体」卡片

SSE 接入复用 `utils/sseBridge.js`（已解决 App 端 SSE 差异）。

---

## 四、改动文件预估

### 后端（新增 9 + 改 3）

| 文件 | 类型 | 说明 |
|---|---|---|
| `agent/AgentService.java` | 新增 | 接口（run / cancel / 历史） |
| `agent/impl/AgentServiceImpl.java` | 新增 | ReAct 循环 + 协议解析 + 上下文组装 + 落库 |
| `agent/AgentAction.java` | 新增 | LLM 动作 DTO（thought / action / tool / args / output） |
| `agent/AgentActionParser.java` | 新增 | JSON 提取 + 解析 + 容错重试 |
| `agent/AgentPromptBuilder.java` | 新增 | system 提示词（人设 + 协议 + **DB 生成的工具清单**） |
| `agent/AgentToolCatalog.java` | 新增 | 从 `sys_aitools_tool` 查工具 + 过滤出可用的 + 生成说明 |
| `agent/AgentArgsMapper.java` | 新增 | LLM args → NodeExecutor 的 (inputs, params) 映射 |
| `controller/AgentController.java` | 新增 | SSE 端点 + 历史端点 |
| `entity/AgentRun.java` + `mapper/AgentRunMapper.java` | 新增 | 持久化 |
| `sql/v1_2026_10_27_agent_run.sql` | 新增 | 建表（**随代码走**，AGENTS.md 要求） |
| `ai/AiClient.java` | 改 | 加 `chatMessages(List<Map>, cred)` 重载 |
| `common/ResultCode.java` | 改 | 加 agent 错误码 |
| `common/Constants.java` | 改 | 加 agent 相关常量（maxSteps / 截断长度 / 并发上限） |

### 前端（新增 2 + 改 1~2）

| 文件 | 类型 | 说明 |
|---|---|---|
| `pages/agent.vue` | 新增 | 智能体对话页 |
| `api/agent.js` | 新增 | SSE 接入（复用 `utils/sseBridge.js`） |
| `pages/index.vue` | 改 | 首页入口卡片 |

---

## 五、风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| 模型不按 JSON 协议输出 | 高 | 提取首个 JSON 块 + 重试 1 次 + 明确报错；system 提示词带示例 |
| 无限循环 / 烧钱 | 高 | `maxSteps=8` + 连续失败计数 + 单用户同时只允许 1 个运行中 |
| token 爆炸 | 中 | 工具结果截断 2000 字 + 步数上限 + BYOK 让用户用自己的 Key |
| BYOK 换成上下文小的模型 | 中 | 上下文条数上限 + 超限明确报错 |
| 附件传不进去 | 中 | 复用现有 multipart 上传链路；注意交接 §7.1 的「文件传完即丢」坑 —— agent 要把附件落临时文件并把路径喂给工具 |
| App 端 SSE / markdown | 中 | 复用 `sseBridge` + `MarkdownView`（已有 rich-text 适配） |
| 工具参数非法 | 中 | 白名单校验 → 回灌错误让模型自纠 |
| 工具清单随 DB 变化导致 prompt 漂移 | 低 | 清单是运行时从 DB 生成的，不写死；prompt 里固定「只输出 JSON」的协议部分 |

---

## 六、与 v1 的差异（勘测带来的修正）

| # | v1 的判断 | 勘测后的修正 | 原因 |
|---|---|---|---|
| 1 | 工具清单要写**代码内注册表**（`AgentToolRegistry`） | 工具清单**从 `sys_aitools_tool` 动态生成** | 勘测发现 15 个工具**已经登记了 description + input_type + output_type**，这三样就是 LLM 需要的工具说明 |
| 2 | 工具参数 schema 缺失是「初版不落 DB」的理由 | 不再是理由 | `input_type` 已经隐含了参数形态（text/file/image/audio） |
| 3 | 6 个工具 | 8 个工具（补 `doc-to-text`、`doc-keypoint-extract`） | 勘测确认 `file → text` 类工具在 DB 里已有登记，且 `NodeExecutor` 已能跑 |
| 4 | 未考虑 BYOK | **BYOK 列为决策 7** | 智能体最烧 token，必须支持用户自带 Key（`ResolvedCredential` 现成） |
| 5 | 未考虑「文件传完即丢」 | 列入风险 + 附件落临时文件 | 交接 §7.1 的坑，agent 会踩 |
| 6 | 未考虑逐帧日志 | steps 落库顺带解决（仅 agent 链路） | 交接 §7.4 的痛点 |

---

## 七、实施计划（分批，每批独立可验证）

### 第 1 批 · 最小闭环（不接工具，纯对话先跑通）
1. `AiClient.chatMessages` 重载
2. `AgentAction` + `AgentActionParser`（容错 + 重试）
3. `AgentPromptBuilder`（先写死 2 个工具，DB 版下一批）
4. `AgentServiceImpl` 循环骨架 + `AgentController` SSE 端点
5. `pages/agent.vue`（先只显示最终答案）
   → **验证**：输入「把这段会议记录整理成会议纪要」能出结果

### 第 2 批 · 接工具 + 落库
6. `AgentToolCatalog`（DB 生成工具清单）
7. `AgentArgsMapper` + 复用 `NodeExecutor` 执行
8. 工具结果回灌上下文
9. `sys_agent_run` 建表 + 落库 + 历史查询接口

### 第 3 批 · 体验
10. 前端流式展示每一步（工具卡片 + 参数 + 耗时 + 结果折叠）
11. 首页入口 + 附件上传（落临时文件）
12. BYOK 接入

### 第 4 批 · 收尾
13. 协议重试策略、截断策略、并发限制调优
14. 错误码 + 取消 + 错误文案收敛
15. README + 交接文档

---

## 八、待确认

1. **§二 的原目标反推对不对？**（智能体 = 毕业设计收官模块：从「工具箱+工作流」升级到「AI 自己规划调用工具」）
2. **§3.3 的工具范围**：8 个（文本 4 + 文件 2 + 音频 1 + 图像 1）合不合适？
   要不要把 image→image 那几个也接上（产物以 URL 形式回灌）？
3. **入口位置**：首页工具区加卡片 / 「我的」里 / 独立 tab？
4. **运行记录落库**：按 §3.7 落 `sys_agent_run`（要建表 + 历史接口），
   还是初版只做实时流式不落库（实现量小很多，但就没有回放）？

---

## 九、实施前的流程评审（2026-10-27，实读代码逐条核对）

设计定稿后、动手前，把「总体流程」对着现有代码又过了一遍，发现 **7 个问题**，
其中 2 个是阻断级。全部已在实现中修掉。

| # | 问题 | 级别 | 根因 | 修法 |
|---|---|---|---|---|
| 1 | **文件类工具在智能体里「只解析不加工」** | 阻断 | `NodeExecutor.executeOne` 按 `input_type` 单类型分派：`doc-keypoint-extract` 的 input_type=file → 只走 `documentParser.parse()`，**「重点提取」的提示词根本没执行**。工作流里同理（既有行为，非新引入） | 智能体的工具目录**显式声明管线**，不靠 input_type 猜：`doc-keypoint-extract → [parse, process]`；`NodeExecutor` 新增 public `processText()`，复用同一份提示词解析，零逻辑重复 |
| 2 | **LLM 怎么引用附件** | 阻断 | 模型不知道服务器本地路径，也不该知道（COS 签名 URL 还带时效） | 附件给模型的是**稳定 ID**（`file_1`/`file_2`）+ 文件名；服务端维护 ID→真实地址映射；模型填 ID，查不到就回灌错误让它自纠 |
| 3 | 一次 agent 步骤 = **两次 LLM 调用** | 重要 | agent 决策 1 次 + 工具内部加工 1 次（如 work-summary 自己也调 AI） | 文档写明；由步数上限兜住总成本 |
| 4 | **SSE 线程池会被长任务占满** | 重要 | `streamExecutor`（core 8/max 32）是给「一问一答」工具用的（首字延迟敏感）；智能体一次运行占一个线程几十秒~几分钟 | 单开 `agentExecutor`（core 2 / max 4 / queue 20），长任务不与普通工具抢线程 |
| 5 | **SSE 断开 = 取消** | 重要 | 用户关页面 → emitter 写失败 → IOException | 循环里把写失败当成取消信号，终止本轮（不吞掉，否则会继续烧 token） |
| 6 | 工具历史会混进工作流历史 | 一般 | `NodeExecutor` 写的是 `createWorkflowNodeHistory`（source_type=2 工作流来源） | `source_type` 增 3=智能体调用，agent 走自己的历史写入 |
| 7 | 附件白名单 | 一般 | 初版按图片白名单写死，会拒掉文档/录音（而那正是 agent 的主要用途） | 复用 `Constants.ALLOWED_FILE_EXTENSIONS`（图片+文档+音频的总白名单），不另写一份 |

### 另外两处实现取舍

| 取舍 | 决定 | 理由 |
|---|---|---|
| SSE 端点的附件怎么传 | **先 `POST /api/file/upload` 上传拿 URL，SSE 端点只收 JSON** | 前端 SSE 是「JSON POST + 增量读响应」（App 端还要跨 renderjs 层），multipart 带不过去 |
| 运行记录落库 | 本轮**先不落库**（第 2 批做） | 先把「能跑通」这件事闭环；落库涉及建表 + 历史接口，单独一批更稳 |

---

## 十、实施状态

| 批次 | 内容 | 状态 |
|---|---|---|
| 第 1 批 | `AiClient.chatMessages` 多轮重载、协议解析器（**14 个单测**）、工具目录、提示词构造、ReAct 循环、SSE 端点、前端页面 + 首页入口 | ✅ 已完成 |
| 第 2 批 | `sys_agent_run` 建表 + 落库 + 历史查询接口（列表/详情）+ 工具历史 `source_type=3` | ✅ 已完成 |
| 第 3 批 | 前端历史页（列表 + 详情弹层）、附件上传、BYOK 模型选择器 | ✅ 已完成 |
| 第 4 批 | 取消按钮、独立错误码段、答案打字机、README + 交接文档 | ✅ 已完成 |

### 第 4 批：App 端可用性收口

这一批的目标是「**实际可落地的 App 智能体**」——把前面提到的功能全部做到能真用。
动手前先做了一次**端到端链路核查**，结果发现 **3 个只有真机才会暴露的问题**：

| # | 问题 | 后果 | 修法 |
|---|---|---|---|
| 1 | **App 端 SSE 缺 `<sse-renderjs>` 组件** | App 端请求直接失败：「流式组件未就绪，请返回上一页重试」（`stream.js` 靠 `getSseEmitter()` 拿视图层组件，页面不注册就没有） | agent 页加 `<sse-renderjs />`（`#ifdef APP-PLUS`）+ 引入组件。**已用 App 端产物验证**：`createVNode(vi)` 在 agent 页出现，H5 产物里则没有 |
| 2 | **自动滚动用 `scroll-top` 设大值** | App 端滚不到底（scroll-view 的 scroll-top 对超大值不可靠） | 改成 `scroll-into-view` + 末尾锚点元素（两端通用） |
| 3 | **附件选择走 `fileType: 'file'`** | App 端**选不了文档/录音**（`uni.chooseFile` / `chooseMessageFile` 在 App 端不被支持） | 按端分流：App 端走全平台都支持的 `uni.chooseImage`（只能选图片，界面上明确提示），H5 端才放开全部类型 |

> 这三条都属于「**H5 端测不出来**」的问题 —— 所以 App 端产物必须单独构建、单独验。

| 新增 | 说明 |
|---|---|
| 取消功能 | 协作式取消：内存里 `runId → AtomicBoolean`，循环在每个自然检查点看一眼。<b>不用 interrupt</b>（interrupt 会在任意位置抛异常，可能打断「写运行记录」这类收尾动作，留下半截数据）。首帧下发 runId（`--- [运行:xxx] ---`），前端据此调 `POST /api/agent/cancel` |
| 独立错误码 | `7xxxx` 段扩成「工作流 / 智能体编排」，新增 7 个：`AGENT_PARSE_FAILED` / `AGENT_ACTION_INVALID` / `AGENT_TOOL_FAILED` / `AGENT_STEPS_EXCEEDED` / `AGENT_CANCELLED` / `AGENT_RUN_NOT_FOUND` / `AGENT_MODEL_UNAVAILABLE`（前后端一一对应） |
| 答案打字机 | 答案在动作 JSON 的 `output` 字段里是**整段**到达的；前端收到后逐字吐出，视觉效果一样且**不动协议**。打字期间 MarkdownView 走 `streaming` 纯文本分支（避免每个字重解析 markdown） |

### 第 2~3 批补上的东西

| 项 | 说明 |
|---|---|
| `sql/v1_2026_10_27_agent_run.sql` | 建表（随代码走，AGENTS.md 要求） |
| `common/AgentRunStatusEnum` | 0待运行 1运行中 2成功 3失败 4已取消（比工作流少一个「部分失败」—— 智能体没有分支） |
| `entity/AgentRun` + `mapper/AgentRunMapper` | 持久化 |
| `agent/AgentStepsCodec` | 步骤 JSON 编解码（**纯逻辑，抽出来才能单测**）+ `AgentStepsCodecTest`（5 个用例，含换行/引号/反斜杠/Emoji 往返） |
| `vo/AgentRunVO` | 出参（状态中文由枚举给，前端不硬编码映射） |
| `HistoryService.createAgentToolHistory` | 智能体调工具的历史（`source_type=3`，与工作流节点历史分开记） |
| 运行记录兜底 | SSE 断开（用户关页面）→ 记录标「失败」，**不会停在「运行中」** |
| 前端 `pages/agent-history.vue` | 运行记录列表 + 详情弹层（目标/每一步/最终答案） |
| 前端 BYOK | 智能体页的模型选择器接上 `listUserAiModels()`，只列 text/vision 能力的模型 |

### 还没做的（明确记下来）

| 项 | 原因 |
|---|---|
| **App 端只能选图片附件** | `uni.chooseFile` / `chooseMessageFile` 在 App 端不被支持。要放开文档/录音得上插件市场（iOS 还要原生插件）或用 `plus.io` 自绘文件选择器 —— 是个独立模块，不在本次范围 |
| 工作流的逐帧日志 | 交接 §7.4 那个痛点**只在智能体这条链路解决了**（steps 存了），工作流仍是只存终态 |
| 答案的真·流式 | 打字机是前端模拟的；要模型边写边吐得改协议（让答案单独走一条流），代价大，暂不做 |

### App 端已知限制（使用时要知道的）

| 限制 | 说明 |
|---|---|
| 附件仅图片 | 见上表；App 端想传文档/录音请在 H5 端上传 |
| 一次运行独占一个线程 | 智能体跑在 `agentExecutor`（core 2 / max 4），并发上限低是刻意的 —— 一次运行 = 多次 LLM 调用，是长任务 |
| 需要登录 | SSE 走 Bearer token；App 端由 renderjs 组件带 token |
