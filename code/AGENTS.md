# 智汇工具箱 / AiToolsBox — 开发规范

> 本文档是项目级开发规范。下次在本项目做改动时必须遵守。
> 与本规范冲突的旧代码，**新写的部分**按本规范来；改老代码时按"最小修改"原则不要顺手重构。

---

## 1. 总体原则

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

## 8. 改动流程

1. 改前先说：本模块改动 / 影响范围 / 验证方式
2. 用户确认后再动
3. 改完跑 `mvn compile` 验证（前端跑 vite build）
4. **不主动 commit / push** — 等用户测试通过
5. 测试有 bug → 整理问题 + 分析根因 + 提方案 → 等用户确认 → 再改

---

## 9. 沟通 / 反馈

- 中文回复，技术术语保留英文
- 简明扼要，不堆方案、不绕弯
- 较大改动 / 删代码 / 改 DB / 改公共接口 → **先沟通 + 给多方案** + 等用户确认
