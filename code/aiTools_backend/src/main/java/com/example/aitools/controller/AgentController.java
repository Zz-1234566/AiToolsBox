package com.example.aitools.controller;

import com.example.aitools.agent.AgentAttachment;
import com.example.aitools.agent.AgentService;
import com.example.aitools.agent.dto.AgentCancelRequest;
import com.example.aitools.agent.dto.AgentRunRequest;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Result;
import com.example.aitools.common.StreamHelper;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AgentRunVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 智能体 Controller。
 *
 * <p>只做：收参 / 鉴权 / 校验 / 交给 Service / 返回 SSE。
 * <b>业务逻辑全在 {@link AgentService}</b>（AGENTS.md 第 2 节）。
 *
 * <p><b>为什么是 SSE 而不是普通 JSON</b>：智能体是「想一步 → 调一步」的循环，
 * 用户要看到**过程**（想什么、调了什么、结果是什么），
 * 否则一个跑 30 秒的请求在页面上就只是一个转圈。
 *
 * <p><b>为什么附件是 JSON 里的 URL 而不是 multipart</b>：
 * 前端的 SSE 走「JSON POST + 增量读响应」（App 端还要跨 renderjs 层），
 * multipart 直传带不过去。附件先用现成的 {@code POST /api/file/upload} 上传拿 URL。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    /** 单次运行的附件数量上限（防止一次塞几十个文件把上下文与内存打爆） */
    private static final int MAX_ATTACHMENTS = 10;

    private final AgentService agentService;
    private final AuthUtil authUtil;

    /** 智能体专用线程池（长任务，不能和普通 SSE 工具共用） */
    @Qualifier(ExecutorConfig.AGENT_EXECUTOR)
    private final ExecutorService agentExecutor;

    /**
     * 跑一次智能体（SSE 流式）。
     *
     * @param req     请求体（目标 / 模型 / 附件）
     */
    @PostMapping(value = "/run", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter run(@RequestBody AgentRunRequest req, HttpServletRequest request) {

        Long userId = authUtil.getUserIdFromRequest(request);

        if (req == null || req.getGoal() == null || req.getGoal().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请输入你的目标");
        }
        if (req.getGoal().length() > Constants.AI_INPUT_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "目标内容过长（最多 " + Constants.AI_INPUT_MAX_LENGTH + " 字）");
        }
        List<AgentAttachment> attachments = toAttachments(req.getFiles());
        log.info("[agent] 用户 {} 发起运行，附件 {} 个", userId, attachments.size());

        return StreamHelper.stream(agentExecutor, emitter ->
                agentService.run(userId, req.getSessionId(), req.getGoal(), attachments, req.getModelCode(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * 取消一次正在运行的智能体。
     * <p>
     * runId 由运行时的**首帧**下发（{@code --- [运行:xxx] ---}）——
     * 前端必须先拿到它才能调取消。
     *
     * @param req 含 runId
     */
    @PostMapping("/cancel")
    public Result<Boolean> cancel(@RequestBody AgentCancelRequest req, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (req == null || req.getRunId() == null || req.getRunId().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少运行 ID");
        }
        boolean cancelled = agentService.cancel(userId, req.getRunId());
        if (!cancelled) {
            // 已经跑完 / 正在收尾 / 不是自己的运行 —— 都当「没得取消」
            throw new BusinessException(ResultCode.AGENT_RUN_NOT_FOUND.getCode(),
                    "该运行已结束或不存在，无法取消");
        }
        return Result.success("已发送取消请求", true);
    }

    /**
     * 我最近的智能体运行记录（不含步骤明细）。
     *
     * @param limit 条数上限（1~50）
     */
    @GetMapping("/runs")
    public Result<List<AgentRunVO>> listRuns(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(agentService.listRuns(userId, limit));
    }

    /**
     * 运行详情（含步骤明细）。
     *
     * @param runId 运行 ID
     */
    @GetMapping("/runs/{runId}")
    public Result<AgentRunVO> getRun(@PathVariable("runId") String runId,
                                     HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        AgentRunVO vo = agentService.getRun(userId, runId);
        if (vo == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "运行记录不存在");
        }
        return Result.success(vo);
    }

    /**
     * 请求附件 → 智能体附件（给模型的是 file_1/file_2 这种稳定 ID，不是真实地址）。
     */
    private List<AgentAttachment> toAttachments(List<AgentRunRequest.Attachment> files) {
        List<AgentAttachment> list = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return list;
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "附件最多 " + MAX_ATTACHMENTS + " 个");
        }
        for (int i = 0; i < files.size(); i++) {
            AgentRunRequest.Attachment f = files.get(i);
            if (f == null || f.getUrl() == null || f.getUrl().isBlank()) {
                continue;
            }
            list.add(new AgentAttachment(
                    "file_" + (i + 1),
                    (f.getName() == null || f.getName().isBlank()) ? ("附件" + (i + 1)) : f.getName(),
                    f.getUrl(),
                    null));
        }
        return list;
    }
}
