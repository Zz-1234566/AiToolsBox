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

    @Override
    public String run(Long userId, String goal, List<AgentAttachment> attachments,
                      String modelCode, Consumer<String> onFrame) {
        if (goal == null || goal.isBlank()) {
            throw ErrorFactory.of(com.example.aitools.common.ResultCode.PARAM_MISSING, "请输入你的目标");
        }
        long start = System.currentTimeMillis();

        // 0) 先建「运行中」记录：这样即使进程被 kill / 用户关页面，
        //    也留下一条「运行中」的记录（可被识别为异常中断），而不是什么都查不到
        String runId = createRunRecord(userId, goal);
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        runningAgents.put(runId, cancelFlag);
        // 首帧就把 runId 下发：前端要靠它才能调取消（取消接口的入参就是 runId）
        onFrame.accept(frame(FRAME_RUN, runId));

        // 1) 工具清单（DB 生成）+ system 提示词
        List<AgentToolCatalog.AgentTool> tools = catalog.listTools();
        String systemPrompt = promptBuilder.buildSystemPrompt(tools, attachments);

        // 2) 多轮上下文：system + 目标，之后每轮追加 assistant(动作) + user(工具结果)
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", goal));

        // 3) 凭据（BYOK：用户自己的模型 Key 优先）
        ResolvedCredential cred = resolveCred(userId, modelCode);

        try {
            return doRun(userId, runId, messages, attachments, cred, cancelFlag, onFrame);
        } catch (Exception e) {
            // 兜底：SSE 断开（用户关页面）会抛 IOException，
            // 这时运行记录不能停在「运行中」—— 标成失败，列表里能看出这次没跑完
            runningAgents.remove(runId);
            failRunRecord(runId, "运行中断：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                    new ArrayList<>(), start);
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
            String raw = aiClient.chatMessages(messages, cred);

            AgentAction action = AgentActionParser.parse(raw);
            if (action == null) {
                // 解析失败：把模型的原话和错误说明回灌，让它自己纠正一次
                if (parseFailures >= Constants.AGENT_PARSE_RETRY) {
                    String msg = "模型未能按约定格式输出，已重试 " + Constants.AGENT_PARSE_RETRY + " 次仍失败";
                    failRunRecord(runId, msg, steps, start);
                    throw ErrorFactory.of(com.example.aitools.common.ResultCode.AGENT_PARSE_FAILED, msg);
                }
                parseFailures++;
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
                onFrame.accept(frame(FRAME_THOUGHT, action.getThought()));
            }

            // --- 收尾：模型给出最终答案 ---
            if (AgentAction.ACTION_FINAL.equals(action.getAction())) {
                onFrame.accept(frame(FRAME_ANSWER, ""));
                onFrame.accept(action.getOutput());
                onFrame.accept(frame(FRAME_DONE, ""));
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
            String toolOutput;
            try {
                String input = resolveInput(tool, action, attachments, lastToolOutput);
                toolOutput = catalog.invoke(tool, input, new HashMap<>(), userId, runId);
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
            stepRecord.setThought(action.getThought());
            stepRecord.setTool(tool.getCode());
            stepRecord.setArgs(argsJson(action));
            stepRecord.setCostMs(cost);
            steps.add(stepRecord);

            // 工具结果回灌前截断（防 token 爆炸）
            String forModel = toolOutput;
            boolean truncated = false;
            if (forModel != null && forModel.length() > Constants.AGENT_TOOL_RESULT_MAX_CHARS) {
                forModel = forModel.substring(0, Constants.AGENT_TOOL_RESULT_MAX_CHARS) + "…（结果已截断）";
                truncated = true;
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
    private String frame(String prefix, String content) {
        String body = content == null ? "" : content.replace("\r", " ").replace("\n", " ");
        return "--- [" + prefix + (body.isEmpty() ? "" : ":" + body) + "] ---";
    }
}
