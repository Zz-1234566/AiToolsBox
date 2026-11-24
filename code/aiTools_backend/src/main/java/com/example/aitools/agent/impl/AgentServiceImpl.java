package com.example.aitools.agent.impl;

import com.example.aitools.agent.AgentAction;
import com.example.aitools.agent.AgentActionParser;
import com.example.aitools.agent.AgentAttachment;
import com.example.aitools.agent.AgentPromptBuilder;
import com.example.aitools.agent.AgentService;
import com.example.aitools.agent.AgentToolCatalog;
import com.example.aitools.agent.dto.AgentStep;
import com.example.aitools.common.AgentRunStatusEnum;
import com.example.aitools.config.AgentAiConfig;
import com.example.aitools.entity.AgentRun;
import com.example.aitools.mapper.AgentRunMapper;
import com.example.aitools.vo.AgentRunVO;
import com.example.aitools.ai.AiClient;
import com.example.aitools.ai.ResolvedCredential;
import com.example.aitools.common.Constants;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.exception.ErrorFactory;
import com.example.aitools.service.ModelCredentialResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 智能体核心：ReAct 循环（想 → 调工具 → 看结果 → 再想）。
 *
 * <p><b>循环结构</b>：
 * <pre>
 *   目标 → LLM
 *      ├─ action=final     → 输出答案，结束
 *      └─ action=tool_call → 执行工具 → 结果回灌 → 继续
 * </pre>
 *
 * <p><b>四条硬约束</b>（没有它们，一次跑飞就烧掉用户的钱）：
 * <ol>
 *   <li>步数上限（{@link Constants#AGENT_MAX_STEPS}）</li>
 *   <li>模型输出解析失败 → 把错误回灌让它自己纠正，只给 1 次机会</li>
 *   <li>连续工具失败 2 次 → 终止</li>
 *   <li>工具结果回灌前截断（{@link Constants#AGENT_TOOL_RESULT_MAX_CHARS}）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    /** 时间格式化（与工作流运行记录同口径） */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 智能体专用模型配置（环境变量优先，见 AgentAiConfig） */
    private final AgentAiConfig agentAiConfig;

    /** JSON 序列化（步骤落库 / 解析） */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AiClient aiClient;
    private final AgentToolCatalog catalog;
    private final AgentPromptBuilder promptBuilder;
    private final ModelCredentialResolver credentialResolver;
    private final AgentRunMapper agentRunMapper;

    /**
     * 运行中的智能体 → 取消标志。
     * <p>
     * <b>为什么用内存标志而不是直接 interrupt 线程</b>：interrupt 会在任意位置抛
     * InterruptedException，可能打断「写运行记录」这类收尾动作，留下半截数据；
     * 标志位是<b>协作式</b>的 —— 循环在每个自然检查点看一眼，不点了就干净退出。
     */
    private final Map<String, AtomicBoolean> runningAgents = new ConcurrentHashMap<>();

    /** 会话服务：把每次运行挂到会话上，并注入历史上下文 */
    private final com.example.aitools.service.AgentSessionService agentSessionService;

    /** 用户画像服务：注入画像 + 运行后异步抽取 */
    private final com.example.aitools.service.AgentProfileService agentProfileService;

    @Override
    public String run(Long userId, String sessionId, String goal, List<AgentAttachment> attachments,
                      String modelCode, Consumer<String> onFrame) {
        return run(userId, sessionId, goal, attachments, modelCode, false, onFrame);
    }

    @Override
    public String run(Long userId, String sessionId, String goal, List<AgentAttachment> attachments,
                      String modelCode, boolean regenerate, Consumer<String> onFrame) {
        if (goal == null || goal.isBlank()) {
            throw ErrorFactory.of(com.example.aitools.common.ResultCode.PARAM_MISSING, "请输入你的目标");
        }
        long start = System.currentTimeMillis();

        // 0) 会话：为空则新建；同时把本次目标落成一条 user 消息
        com.example.aitools.entity.AgentSession session = sessionId == null || sessionId.isBlank()
                ? createAndGetSession(userId)
                : agentSessionService.requireSession(userId, sessionId);
        agentSessionService.autoTitle(session, goal);
        if (regenerate) {
            // 重新生成：不新增 user 消息，复用会话里最后一条用户消息及其附件。
            // 附件本来就挂在消息 meta 里，按对话记录还原即可 —— 不必让前端再传一次。
            com.example.aitools.vo.AgentMessageVO lastUser = findLastUserMessage(userId, session);
            if (lastUser != null) {
                if (attachments == null || attachments.isEmpty()) {
                    attachments = attachmentsFromMeta(lastUser.getMeta());
                }
                if (attachments != null && !attachments.isEmpty()) {
                    log.info("[agent] 重新生成：从会话恢复 {} 个附件", attachments.size());
                }
            }
        } else {
            agentSessionService.appendMessage(session, "user", goal, buildUserMeta(attachments));
        }
        // 本轮目标的序号：注入历史时把它排除（下面单独作为本轮 user 消息加入）
        int currentGoalSeq = session.getMessageCount() == null ? 0 : session.getMessageCount();

        // 0) 先建「运行中」记录：这样即使进程被 kill / 用户关页面，
        //    也留下一条「运行中」的记录（可被识别为异常中断），而不是什么都查不到
        String runId = createRunRecord(userId, goal);
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        runningAgents.put(runId, cancelFlag);
        // 首帧就把 runId 下发：前端要靠它才能调取消（取消接口的入参就是 runId）
        onFrame.accept(frame(FRAME_RUN, runId));
        // 会话ID：前端据此记录当前会话、刷新侧边栏
        onFrame.accept(frame(FRAME_SESSION, session.getSessionId()));

        java.util.concurrent.atomic.AtomicReference<String> finalAnswer = new java.util.concurrent.atomic.AtomicReference<>("");
        java.util.concurrent.atomic.AtomicReference<List<com.example.aitools.agent.dto.AgentStep>> stepsRef =
                new java.util.concurrent.atomic.AtomicReference<>(new ArrayList<>());
        // 本轮生成的图片产物（落 meta，供历史回放）
        List<String> runImages = new ArrayList<>();
        // 前置步骤（拼提示词 / 取凭据 / 压缩上下文）也必须进 try：
        // 之前放在 try 外，一旦抛异常（如模型凭据失效、压缩调用失败），
        // 运行记录会永久停在「运行中」，用户既看不到回答也看不到失败原因。
        try {
            // 1) 工具清单（DB 生成）+ system 提示词
            List<AgentToolCatalog.AgentTool> tools = catalog.listTools();
            String profileText = agentProfileService.getProfileText(userId);
            String systemPrompt = promptBuilder.buildSystemPrompt(tools, attachments, profileText);

            // 2) 凭据（BYOK：用户自己的模型 Key 优先）—— 压缩也要用它调模型
            ResolvedCredential cred = resolveCred(userId, modelCode);

            // 3) 多轮上下文：system +（历史摘要）+ 历史原文 + 本次目标
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(message("system", systemPrompt));
            maybeCompressContext(userId, session, cred);
            appendHistoryContext(messages, userId, session, currentGoalSeq);
            messages.add(message("user", buildUserTurn(goal, attachments)));

            doRun(userId, runId, messages, attachments, cred, cancelFlag, finalAnswer, stepsRef, runImages, onFrame);
            String answer = finalAnswer.get();
            // 成功：把最终答案落成一条 assistant 消息（meta 里带运行信息，供回放）
            agentSessionService.appendMessage(session, "assistant", answer,
                    buildAssistantMeta(runId, System.currentTimeMillis() - start, stepsRef.get(), runImages));
            // 画像抽取：异步，不阻塞本次回答
            agentProfileService.updateAsync(userId, session.getSessionId(),
                    agentSessionService.messages(userId, session.getSessionId(), 12), cred);
            return answer;
        } catch (Exception e) {
            // 兜底：SSE 断开（用户关页面）会抛 IOException，
            // 这时运行记录不能停在「运行中」—— 标成失败，列表里能看出这次没跑完
            runningAgents.remove(runId);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            failRunRecord(runId, "运行中断：" + reason, new ArrayList<>(), start);
            // 失败也落一条 assistant 消息，保证回放时上下文完整
            agentSessionService.appendMessage(session, "assistant", "[运行中断] " + reason,
                    buildAssistantMeta(runId, System.currentTimeMillis() - start, stepsRef.get(), runImages));
            throw e;
        }
    }

    /**
     * 循环主体（独立方法，便于在 {@link #run} 里统一兜底异常）。
     */
    private String doRun(Long userId, String runId, List<Map<String, String>> messages,
                         List<AgentAttachment> attachments,
                         ResolvedCredential cred,
                         AtomicBoolean cancelFlag,
                         java.util.concurrent.atomic.AtomicReference<String> finalAnswer,
                         java.util.concurrent.atomic.AtomicReference<List<com.example.aitools.agent.dto.AgentStep>> stepsRef,
                         List<String> runImages,
                         Consumer<String> onFrame) {
        long start = System.currentTimeMillis();
        List<AgentStep> steps = new ArrayList<>();

        // 4) 循环
        int step = 0;
        int parseFailures = 0;
        int consecutiveFailures = 0;
        String lastToolOutput = null;

        while (step < Constants.AGENT_MAX_STEPS) {
            // 取消检查点：每一步开始前看一眼（协作式，不用 interrupt）
            if (cancelFlag.get()) {
                cancelRunRecord(runId, steps, start);
                throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_CANCELLED, "已取消");
            }
            // 上下文占用估算：日志 + 下发给前端展示（不参与截断决策）
            int ctxTokens = logContextUsage(runId, step, messages);
            onFrame.accept(frame(FRAME_CONTEXT, ctxTokens + ":" + Constants.AGENT_CONTEXT_WINDOW_TOKENS));

            // 思考阶段计时：先通知前端，模型返回后给最终耗时
            onFrame.accept(frame(FRAME_THOUGHT_START, ""));
            long thinkStart = System.currentTimeMillis();
            String raw = aiClient.chatMessages(messages, cred);
            long thinkCost = System.currentTimeMillis() - thinkStart;

            AgentAction action = AgentActionParser.parse(raw);
            if (action == null) {
                // 解析失败：把模型的原话和错误说明回灌，让它自己纠正一次
                if (parseFailures >= Constants.AGENT_PARSE_RETRY) {
                    String msg = "模型未能按约定格式输出，已重试 " + Constants.AGENT_PARSE_RETRY + " 次仍失败";
                    failRunRecord(runId, msg, steps, start);
                    throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_PARSE_FAILED, msg);
                }
                parseFailures++;
                // 本步思考必须收尾：否则前端那条「正在思考」永远停在计时中，
                // 下一轮又开始新的思考 → 界面上出现两条并行的「正在思考」
                onFrame.accept(frame(FRAME_THOUGHT_COST, String.valueOf(thinkCost)));
                messages.add(message("assistant", raw == null ? "" : raw));
                messages.add(message("user",
                        "[格式错误] 你的上一次输出无法解析。请**只输出一个 JSON 对象**，"
                                + "形如 {\"thought\":\"...\",\"action\":\"tool_call\",\"tool\":\"工具编码\",\"args\":{...}} "
                                + "或 {\"thought\":\"...\",\"action\":\"final\",\"output\":\"最终答案\"}。"));
                log.warn("[agent] 模型输出解析失败，第 {} 次回灌纠正", parseFailures);
                continue;
            }

            if (!AgentActionParser.isValid(action)) {
                // 动作不完整（缺 tool / 缺 output / action 类型未知）→ 重试纠正也没用，直接终止
                String msg = "模型返回的动作不完整（action=" + action.getAction() + "），已终止";
                failRunRecord(runId, msg, steps, start);
                throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_ACTION_INVALID, msg);
            }

            if (action.getThought() != null && !action.getThought().isBlank()) {
                // file_1 这种内部 ID 不该露给用户：展示/落库前换回真实文件名
                String thoughtText = humanizeFileRefs(action.getThought(), attachments);
                onFrame.accept(frame(FRAME_THOUGHT, thoughtText));
                // 思考步骤只在「收尾轮」单独落库（纯对话就只有这一条）。
                // 调工具的那一轮，思考会随工具步一起记录 —— 否则同一句思考
                // 会在历史里出现两次（一条思考步 + 一条工具步各带一遍）。
                if (AgentAction.ACTION_FINAL.equals(action.getAction())) {
                    com.example.aitools.agent.dto.AgentStep thoughtStep =
                            new com.example.aitools.agent.dto.AgentStep();
                    thoughtStep.setSeq(steps.size() + 1);
                    thoughtStep.setThought(thoughtText);
                    thoughtStep.setCostMs(thinkCost);
                    steps.add(thoughtStep);
                }
            }
            // 收尾：把本步耗时告诉前端（空思考也要发，保证步骤能结束计时）
            onFrame.accept(frame(FRAME_THOUGHT_COST, String.valueOf(thinkCost)));

            // --- 收尾：模型给出最终答案 ---
            if (AgentAction.ACTION_FINAL.equals(action.getAction())) {
                onFrame.accept(frame(FRAME_ANSWER, ""));
                onFrame.accept(action.getOutput());
                onFrame.accept(frame(FRAME_DONE, ""));
                if (finalAnswer != null) {
                    finalAnswer.set(action.getOutput() == null ? "" : action.getOutput());
                }
                if (stepsRef != null) {
                    stepsRef.set(new ArrayList<>(steps));
                }
                completeRunRecord(runId, action.getOutput(), steps, start);
                runningAgents.remove(runId);
                log.info("[agent] 完成 runId={} step={} cost={}ms", runId, step, System.currentTimeMillis() - start);
                return runId;
            }

            // --- 调工具 ---
            messages.add(message("assistant", raw));

            String toolCode = action.getTool();
            AgentToolCatalog.AgentTool tool = catalog.find(toolCode);
            if (tool == null) {
                consecutiveFailures++;
                String err = "工具不存在或不可用：" + toolCode;
                onFrame.accept(frame(FRAME_TOOL, toolCode + "（不可用）"));
                onFrame.accept(err);
                onFrame.accept(frame(FRAME_TOOL_END, ""));
                messages.add(message("user", "[工具结果] tool=" + toolCode + " 失败\n" + err));
                if (consecutiveFailures >= Constants.AGENT_MAX_CONSECUTIVE_FAILURES) {
                    String msg = "连续 " + consecutiveFailures + " 次工具调用失败，已终止";
                    failRunRecord(runId, msg, steps, start);
                    throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_TOOL_FAILED, msg);
                }
                continue;
            }

            long toolStart = System.currentTimeMillis();
            // 先告诉前端「开始执行哪个工具」，前端据此立刻插入一条进行中的步骤并本地计时
            onFrame.accept(frame(FRAME_TOOL_START, tool.getName() + "（" + tool.getCode() + "）"));
            String toolOutput;
            try {
                String input = resolveInput(tool, action, attachments, lastToolOutput);
                // 只透传 question（图片理解用），其余工具忽略该参数，避免影响既有行为
                Map<String, String> toolParams = new HashMap<>();
                Object q = action.getArgs() == null ? null : action.getArgs().get("question");
                if (q != null && !String.valueOf(q).isBlank()) {
                    toolParams.put("question", String.valueOf(q));
                }
                com.example.aitools.agent.ToolResult toolResult =
                        catalog.invoke(tool, input, toolParams, userId, runId);
                toolOutput = toolResult.getText();
                // 图片产物：追加 [图片:url] 帧，并记录到本轮图片列表（落 meta 供回放）
                if (toolResult.hasImages()) {
                    for (String imgUrl : toolResult.getImageUrls()) {
                        onFrame.accept(frame(FRAME_IMAGE, imgUrl));
                        if (runImages != null) runImages.add(imgUrl);
                    }
                }
                consecutiveFailures = 0;
            } catch (Exception e) {
                consecutiveFailures++;
                toolOutput = "工具执行失败：" + e.getMessage();
                log.warn("[agent] 工具执行失败 tool={}", toolCode, e);
            }
            long cost = System.currentTimeMillis() - toolStart;

            onFrame.accept(frame(FRAME_TOOL, tool.getName() + "（" + tool.getCode() + "）耗时 " + cost + "ms"));

            // 记录这一步（落库用）
            AgentStep stepRecord = new AgentStep();
            stepRecord.setSeq(steps.size() + 1);
            stepRecord.setThought(humanizeFileRefs(action.getThought(), attachments));
            stepRecord.setTool(tool.getCode());
            stepRecord.setArgs(argsJson(action));
            stepRecord.setCostMs(cost);
            steps.add(stepRecord);

            // 工具结果回灌前截断（防 token 爆炸）
            String forModel = toolOutput;
            boolean truncated = false;
            if (forModel != null && forModel.length() > Constants.AGENT_TOOL_RESULT_MAX_CHARS) {
                int beforeTokens = com.example.aitools.common.TokenEstimator.estimate(forModel);
                forModel = forModel.substring(0, Constants.AGENT_TOOL_RESULT_MAX_CHARS) + "…（结果已截断）";
                truncated = true;
                log.info("[agent] 工具结果截断 tool={} 原 {} 字符（约 {} tokens）→ 保留 {} 字符",
                        tool.getCode(), toolOutput.length(), beforeTokens, Constants.AGENT_TOOL_RESULT_MAX_CHARS);
            }
            onFrame.accept(forModel == null ? "" : forModel);
            onFrame.accept(frame(FRAME_TOOL_END, truncated ? "已截断" : ""));

            stepRecord.setOutput(forModel);
            stepRecord.setOutputTruncated(truncated);
            if (toolOutput != null && toolOutput.startsWith("工具执行失败")) {
                stepRecord.setErrorMsg(toolOutput);
            }

            messages.add(message("user", "[工具结果] tool=" + tool.getCode()
                    + " " + (truncated ? "成功（结果已截断）" : "成功") + "\n" + forModel));

            lastToolOutput = toolOutput;
            step++;
        }

        // 步数用尽：把已有的工具结果交给模型做最后总结（再给一次机会出 final）
        String raw = aiClient.chatMessages(messages, cred);
        AgentAction action = AgentActionParser.parse(raw);
        String answer = (action != null && action.getOutput() != null && !action.getOutput().isBlank())
                ? action.getOutput()
                : "已达到最大步数（" + Constants.AGENT_MAX_STEPS + "）仍未得出结论。";
        if (answer.startsWith("已达到最大步数")) {
            failRunRecord(runId, com.example.aitools.common.ResultCode.AGENT_STEPS_EXCEEDED.getMessage(), steps, start);
        }
        onFrame.accept(frame(FRAME_ANSWER, ""));
        onFrame.accept(answer);
        onFrame.accept(frame(FRAME_DONE, ""));
        completeRunRecord(runId, answer, steps, start);
        runningAgents.remove(runId);
        log.info("[agent] 步数用尽，返回兜底结论 runId={} step={} cost={}ms",
                runId, step, System.currentTimeMillis() - start);
        return runId;
    }

    // ==================== 取消 ====================

    @Override
    public boolean cancel(Long userId, String runId) {
        if (runId == null || runId.isBlank()) {
            return false;
        }
        // 只允许取消「自己且正在运行」的：先查库确认归属，再置标志
        AgentRun run = agentRunMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getRunId, runId)
                        .eq(AgentRun::getUserId, userId)
                        .last("LIMIT 1"));
        if (run == null) {
            return false;
        }
        AtomicBoolean flag = runningAgents.get(runId);
        if (flag == null) {
            // 已经跑完了（或正在收尾）→ 没有可取消的运行
            return false;
        }
        flag.set(true);
        log.info("[agent] 收到取消请求 runId={}", runId);
        return true;
    }

    /** 取消收尾：记录标「已取消」（不是「失败」——用户主动停的） */
    private void cancelRunRecord(String runId, List<AgentStep> steps, long start) {
        runningAgents.remove(runId);
        try {
            AgentRun run = new AgentRun();
            run.setRunId(runId);
            run.setStatus(com.example.aitools.common.AgentRunStatusEnum.CANCELLED.getCode());
            run.setStepCount(steps == null ? 0 : steps.size());
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setSteps(com.example.aitools.agent.AgentStepsCodec.toJson(steps));
            run.setFinishedAt(LocalDateTime.now());
            agentRunMapper.update(run, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentRun>()
                    .eq(AgentRun::getRunId, runId));
        } catch (Exception e) {
            log.warn("[agent] 取消收尾落库失败 runId={}", runId, e);
        }
    }

    // ==================== 运行记录 ====================

    /** 建「运行中」记录 */
    private String createRunRecord(Long userId, String goal) {
        AgentRun run = new AgentRun();
        run.setRunId(UUID.randomUUID().toString().replace("-", ""));
        run.setUserId(userId);
        run.setGoal(goal);
        run.setStatus(AgentRunStatusEnum.RUNNING.getCode());
        run.setStepCount(0);
        run.setDuration(0);
        run.setSteps("[]");
        run.setDr(com.example.aitools.common.Constants.DR_NORMAL);
        agentRunMapper.insert(run);
        return run.getRunId();
    }

    /** 成功收尾 */
    private void completeRunRecord(String runId, String finalOutput,
                                   List<AgentStep> steps, long start) {
        updateRunRecord(runId, AgentRunStatusEnum.COMPLETED, finalOutput, null, steps, start);
    }

    /** 失败收尾 */
    private void failRunRecord(String runId, String errorMsg, List<AgentStep> steps, long start) {
        updateRunRecord(runId, AgentRunStatusEnum.FAILED, null, errorMsg, steps, start);
    }

    private void updateRunRecord(String runId, AgentRunStatusEnum status, String finalOutput,
                                  String errorMsg, List<AgentStep> steps, long start) {
        try {
            AgentRun run = new AgentRun();
            run.setRunId(runId);
            run.setStatus(status.getCode());
            run.setFinalOutput(finalOutput);
            run.setErrorMsg(errorMsg == null ? null
                    : (errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg));
            run.setStepCount(steps == null ? 0 : steps.size());
            run.setDuration((int) (System.currentTimeMillis() - start));
            run.setSteps(com.example.aitools.agent.AgentStepsCodec.toJson(steps));
            run.setFinishedAt(LocalDateTime.now());
            agentRunMapper.update(run, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentRun>()
                    .eq(AgentRun::getRunId, runId));
        } catch (Exception e) {
            // 落库失败不影响主流程（用户已经拿到结果了），只记日志
            log.warn("[agent] 运行记录落库失败 runId={}", runId, e);
        }
    }

    /** 工具参数 → JSON 字符串（存步骤用） */
    private String argsJson(AgentAction action) {
        if (action.getArgs() == null || action.getArgs().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(action.getArgs());
        } catch (Exception e) {
            return action.getArgs().toString();
        }
    }

    // ==================== 历史查询 ====================

    @Override
    public List<AgentRunVO> listRuns(Long userId, int limit) {
        int max = Math.min(Math.max(limit, 1), 50);
        List<AgentRun> runs = agentRunMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
                        .eq(AgentRun::getDr, com.example.aitools.common.Constants.DR_NORMAL)
                        .orderByDesc(AgentRun::getId)
                        .last("LIMIT " + max));
        List<AgentRunVO> list = new ArrayList<>();
        for (AgentRun r : runs) {
            list.add(toVO(r, false));
        }
        return list;
    }

    @Override
    public AgentRunVO getRun(Long userId, String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        AgentRun r = agentRunMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getRunId, runId)
                        .eq(AgentRun::getUserId, userId)
                        .eq(AgentRun::getDr, com.example.aitools.common.Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        return r == null ? null : toVO(r, true);
    }

    private AgentRunVO toVO(AgentRun r, boolean withSteps) {
        AgentRunVO vo = new AgentRunVO();
        vo.setRunId(r.getRunId());
        vo.setGoal(r.getGoal());
        vo.setStatus(r.getStatus());
        vo.setStatusLabel(AgentRunStatusEnum.labelOf(r.getStatus() == null ? 0 : r.getStatus()));
        vo.setFinalOutput(r.getFinalOutput());
        vo.setStepCount(r.getStepCount());
        vo.setDuration(r.getDuration());
        vo.setErrorMsg(r.getErrorMsg());
        vo.setCreateTime(r.getCreateTime() == null ? null : r.getCreateTime().format(FMT));
        vo.setFinishedAt(r.getFinishedAt() == null ? null : r.getFinishedAt().format(FMT));
        if (withSteps) {
            vo.setSteps(com.example.aitools.agent.AgentStepsCodec.fromJson(r.getSteps()));
        }
        return vo;
    }



    /**
     * 解析工具入参。
     * <p>
     * <b>附件只能引用 ID</b>：模型不知道服务器本地路径，也不该知道。
     * 它给 {@code args.file = "file_1"}，这里换成真实的文件标识；
     * 给不出对应的附件 → 报错并回灌，让模型改用别的做法。
     */
    private String resolveInput(AgentToolCatalog.AgentTool tool, AgentAction action,
                                List<AgentAttachment> attachments, String fallbackText) {
        Map<String, Object> args = action.getArgs() == null ? Map.of() : action.getArgs();

        if (tool.needsFile()) {
            String fileRef = str(args.get("file"));
            if (fileRef == null) {
                fileRef = str(args.get("filename"));
            }
            if (fileRef == null) {
                fileRef = str(args.get("path"));
            }
            if (fileRef == null) {
                throw ErrorFactory.of(com.example.aitools.common.ResultCode.PARAM_ERROR,
                        "工具 " + tool.getCode() + " 需要文件参数（args.file）");
            }
            AgentAttachment att = findAttachment(fileRef, attachments);
            if (att == null) {
                throw ErrorFactory.of(com.example.aitools.common.ResultCode.PARAM_ERROR,
                        "附件不存在：" + fileRef + "（可用附件：" + describeAttachmentIds(attachments) + "）");
            }
            return att.getFileRef();
        }

        // 文本工具：优先用模型给的 text；没给就用上一步的工具结果（模型说「总结一下」时不用重复喂原文）
        String text = str(args.get("text"));
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (fallbackText != null && !fallbackText.isBlank()) {
            return fallbackText;
        }
        throw ErrorFactory.of(com.example.aitools.common.ResultCode.PARAM_ERROR,
                "工具 " + tool.getCode() + " 需要文字参数（args.text）");
    }

    /**
     * 按 ID 或文件名找附件。
     */
    private AgentAttachment findAttachment(String ref, List<AgentAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        for (AgentAttachment a : attachments) {
            if (a.getId() != null && a.getId().equalsIgnoreCase(ref)) {
                return a;
            }
        }
        for (AgentAttachment a : attachments) {
            if (a.getFileName() != null && a.getFileName().equalsIgnoreCase(ref)) {
                return a;
            }
        }
        return null;
    }

    private String describeAttachmentIds(List<AgentAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "无";
        }
        List<String> ids = new ArrayList<>();
        for (AgentAttachment a : attachments) {
            ids.add(a.getId());
        }
        return String.join(", ", ids);
    }

    /**
     * 解析调用凭据：用户显式选了模型就按 modelCode 查，否则走默认三级回退。
     */
    private ResolvedCredential resolveCred(Long userId, String modelCode) {
        try {
            if (modelCode != null && !modelCode.isBlank()) {
                credentialResolver.requireCapability(modelCode, "text");
                return credentialResolver.resolveByModelCode(userId, modelCode, null);
            }
            // 未选模型：环境变量里配了智能体专用 key 就直接用（OpenCode Go 套餐）
            if (agentAiConfig != null && agentAiConfig.isEnabled()) {
                return new ResolvedCredential(
                        agentAiConfig.getApiKey(),
                        agentAiConfig.getApiUrl(),
                        agentAiConfig.getModel(),
                        "opencode",
                        "env_default");
            }
            return credentialResolver.resolve(userId, "text", null);
        } catch (BusinessException e) {
            // 模型不存在 / 能力不匹配 / 凭据无效 —— 归到智能体自己的错误码，
            // 前端才能给出「所选模型不可用，请重新选择」而不是笼统的参数错误
            throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_MODEL_UNAVAILABLE,
                    e.getMessage() == null ? "所选模型不可用，请重新选择" : e.getMessage());
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 过程帧：{@code --- [内容] ---}（与项目现有 SSE 标记格式一致） */
    /** 压缩后保留的最近原文条数 */
    private static final int CTX_KEEP_RECENT = 6;
    /** 未压缩消息数达到该值时强制压缩 */
    private static final int CTX_MAX_UNCOMPRESSED = 20;

    /**
     * 上下文压缩：占比 ≥60% 或 未压缩消息 ≥20 条时，把较早的消息压成摘要。
     * <p>
     * 保留最近 {@value #CTX_KEEP_RECENT} 条原文保证连贯；摘要写回会话，后续注入时替代原文。
     */
    private void maybeCompressContext(Long userId, com.example.aitools.entity.AgentSession session,
                                      ResolvedCredential cred) {
        int total = session.getMessageCount() == null ? 0 : session.getMessageCount();
        int summarized = session.getSummarizedSeq() == null ? 0 : session.getSummarizedSeq();
        int uncompressed = total - summarized;
        if (uncompressed <= CTX_KEEP_RECENT) {
            return;
        }
        List<com.example.aitools.vo.AgentMessageVO> all =
                agentSessionService.messages(userId, session.getSessionId(), 500);

        // 估算整个会话（摘要 + 全部消息）的占用
        int tokens = com.example.aitools.common.TokenEstimator.estimate(session.getSummary());
        for (com.example.aitools.vo.AgentMessageVO m : all) {
            tokens += com.example.aitools.common.TokenEstimator.estimate(m.getContent());
        }
        int percent = (int) Math.round(tokens * 100.0 / Constants.AGENT_CONTEXT_WINDOW_TOKENS);
        boolean need = percent >= Constants.AGENT_CONTEXT_WARN_PERCENT || uncompressed >= CTX_MAX_UNCOMPRESSED;
        if (!need) {
            return;
        }

        int cutoff = total - CTX_KEEP_RECENT;
        if (cutoff <= summarized) {
            return;
        }

        StringBuilder transcript = new StringBuilder();
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            transcript.append("[此前摘要]\n").append(session.getSummary()).append("\n\n");
        }
        for (com.example.aitools.vo.AgentMessageVO m : all) {
            if (m.getSeq() == null || m.getSeq() <= summarized || m.getSeq() > cutoff) {
                continue;
            }
            transcript.append("user".equals(m.getRole()) ? "用户：" : "助手：")
                    .append(m.getContent() == null ? "" : m.getContent()).append('\n');
        }
        if (transcript.length() == 0) {
            return;
        }
        try {
            String summary = aiClient.chat(
                    "你负责压缩对话上下文。把给定对话压缩成不超过 300 字的中文摘要，"
                            + "保留用户目标、已确认的事实、关键结论与待办；不要编造，不要输出多余说明。",
                    transcript.toString(), cred);
            agentSessionService.updateSummary(session, summary == null ? "" : summary.trim(), cutoff);
            log.info("[agent] 上下文已压缩 sessionId={} 压缩到 seq={} 摘要长度={}（占比 {}%）",
                    session.getSessionId(), cutoff,
                    summary == null ? 0 : summary.length(), percent);
        } catch (Exception e) {
            // 压缩失败不影响本次运行：退化为携带全量历史
            log.warn("[agent] 上下文压缩失败，本次按全量历史执行 sessionId={}: {}",
                    session.getSessionId(), e.getMessage());
        }
    }

    /** 新建会话并返回 */
    private com.example.aitools.entity.AgentSession createAndGetSession(Long userId) {
        com.example.aitools.vo.AgentSessionVO vo = agentSessionService.create(userId);
        return agentSessionService.requireSession(userId, vo.getSessionId());
    }

    /** 把历史消息注入上下文：摘要（若有） + summarizedSeq 之后的历史原文 */
    private void appendHistoryContext(List<Map<String, String>> messages, Long userId,
                                      com.example.aitools.entity.AgentSession session, int currentGoalSeq) {
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            messages.add(message("system", "【本会话此前对话摘要】\n" + session.getSummary()));
        }
        List<com.example.aitools.vo.AgentMessageVO> history =
                agentSessionService.messages(userId, session.getSessionId(), 50);
        int summarized = session.getSummarizedSeq() == null ? 0 : session.getSummarizedSeq();
        for (com.example.aitools.vo.AgentMessageVO h : history) {
            if (h.getSeq() == null || h.getSeq() <= summarized) continue;
            // 本次目标单独作为最后一条 user 消息加入，这里排除
            if (h.getSeq() >= currentGoalSeq) continue;
            String content = h.getContent();
            if ("user".equals(h.getRole())) {
                content = appendHistoryFiles(content, h.getMeta());
            }
            messages.add(message(h.getRole(), content));
        }
    }

    /** 会话里最后一条用户消息（重新生成时复用它的目标与附件） */
    private com.example.aitools.vo.AgentMessageVO findLastUserMessage(
            Long userId, com.example.aitools.entity.AgentSession session) {
        List<com.example.aitools.vo.AgentMessageVO> list =
                agentSessionService.messages(userId, session.getSessionId(), 50);
        for (int i = list.size() - 1; i >= 0; i--) {
            if ("user".equals(list.get(i).getRole())) {
                return list.get(i);
            }
        }
        return null;
    }

    /** 从 user 消息的 meta.files 还原附件（file_1 / file_2 … 顺序与当初一致） */
    private List<AgentAttachment> attachmentsFromMeta(String meta) {
        List<AgentAttachment> out = new ArrayList<>();
        if (meta == null || meta.isBlank()) {
            return out;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode files =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(meta).path("files");
            if (!files.isArray()) {
                return out;
            }
            int i = 0;
            for (com.fasterxml.jackson.databind.JsonNode f : files) {
                String url = f.path("url").asText("");
                if (url.isBlank()) {
                    continue;
                }
                String name = f.path("name").asText("附件" + (i + 1));
                out.add(new AgentAttachment("file_" + (i + 1), name, url, null));
                i++;
            }
        } catch (Exception e) {
            log.warn("[agent] 从消息 meta 还原附件失败: {}", e.getMessage());
        }
        return out;
    }

    /** 把 file_1 这类内部 ID 换成真实文件名（仅用于展示给用户看） */
    private String humanizeFileRefs(String text, List<AgentAttachment> attachments) {
        if (text == null || attachments == null || attachments.isEmpty()) {
            return text;
        }
        String out = text;
        for (AgentAttachment a : attachments) {
            if (a.getId() == null || a.getFileName() == null) {
                continue;
            }
            out = out.replaceAll("\\b" + java.util.regex.Pattern.quote(a.getId()) + "\\b",
                    java.util.regex.Matcher.quoteReplacement(a.getFileName()));
        }
        return out;
    }
    /** user 消息的 meta：记录本次附件，供历史回放展示 */
    /**
     * 本轮 user 消息：把附件清单**贴在用户原话后面**。
     * 只放 system 提示词里，模型经常视而不见 → 明明带了图却回答“没有收到文件”。
     */
    private String buildUserTurn(String goal, List<AgentAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return goal;
        }
        StringBuilder sb = new StringBuilder(goal == null ? "" : goal);
        sb.append("\n\n【本条消息附带的附件】\n");
        for (AgentAttachment a : attachments) {
            sb.append("- ").append(a.getId()).append("：").append(a.getFileName())
                    .append("（").append(kindOf(a.getFileName())).append("）\n");
        }
        sb.append("（以上附件已收到，不要回答“没有收到文件/图片”；需要读取时用上面的附件 ID 调用对应工具。）");
        return sb.toString();
    }

    /** 历史 user 消息补一句附件名，便于多轮追问“那张图” */
    private String appendHistoryFiles(String content, String meta) {
        if (meta == null || meta.isBlank()) {
            return content;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode files =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(meta).path("files");
            if (!files.isArray() || files.isEmpty()) {
                return content;
            }
            StringBuilder names = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode f : files) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(f.path("name").asText("附件"));
            }
            return (content == null ? "" : content) + "\n（附件：" + names + "）";
        } catch (Exception e) {
            return content;
        }
    }

    /** 按扩展名粗判附件类型（只用于给模型看的描述） */
    private String kindOf(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.matches(".*\\.(png|jpg|jpeg|bmp|webp|gif)$")) {
            return "图片";
        }
        if (n.matches(".*\\.(mp3|wav|m4a|aac|amr|flac|ogg)$")) {
            return "音频";
        }
        return "文档";
    }

    private String buildUserMeta(List<AgentAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (AgentAttachment a : attachments) {
            Map<String, Object> f = new HashMap<>();
            f.put("name", a.getFileName());
            f.put("url", a.getFileRef());
            files.add(f);
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("files", files);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    /** assistant 消息的 meta（运行信息，供回放展示） */
    private String buildAssistantMeta(String runId, long durationMs,
                                      List<com.example.aitools.agent.dto.AgentStep> steps,
                                      List<String> images) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("runId", runId);
        meta.put("duration", durationMs);
        if (images != null && !images.isEmpty()) meta.put("images", images);
        // 步骤落库：重新打开会话时据此回放任务规划
        if (steps != null) meta.put("steps", steps);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 打印当前上下文的估算占用。
     * <p>
     * 只读不改：不做任何裁剪，避免影响执行语义。
     * 占比超过 {@link Constants#AGENT_CONTEXT_WARN_PERCENT} 时打 WARN，提示需要考虑压缩策略。
     */
    private int logContextUsage(String runId, int step, List<Map<String, String>> messages) {
        int tokens = com.example.aitools.common.TokenEstimator.estimateMessages(messages);
        int window = Constants.AGENT_CONTEXT_WINDOW_TOKENS;
        int percent = window <= 0 ? 0 : (int) Math.round(tokens * 100.0 / window);
        log.info("[agent] 上下文占用 runId={} step={} messages={} ≈{} tokens（窗口 {}k 的 {}%）",
                runId, step, messages.size(), tokens, window / 1000, percent);
        if (percent >= Constants.AGENT_CONTEXT_WARN_PERCENT) {
            log.warn("[agent] 上下文占比 {}% 已达阈值 {}%，建议启用摘要压缩或滑动窗口 runId={}",
                    percent, Constants.AGENT_CONTEXT_WARN_PERCENT, runId);
        }
        return tokens;
    }

    private String frame(String prefix, String content) {
        String body = content == null ? "" : content.replace("\r", " ").replace("\n", " ");
        return "--- [" + prefix + (body.isEmpty() ? "" : ":" + body) + "] ---";
    }
}
