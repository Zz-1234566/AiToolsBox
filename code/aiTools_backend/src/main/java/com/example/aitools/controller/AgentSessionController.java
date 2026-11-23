package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.service.AgentSessionService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AgentMessageVO;
import com.example.aitools.vo.AgentSessionVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能体会话 Controller。
 * <p>
 * 只做：收参 / 鉴权 / 交给 Service。业务全在 {@link AgentSessionService}。
 */
@RestController
@RequestMapping("/api/agent/sessions")
@RequiredArgsConstructor
public class AgentSessionController {

    private final AgentSessionService sessionService;
    private final AuthUtil authUtil;

    /** 新建会话 */
    @PostMapping
    public Result<AgentSessionVO> create(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(sessionService.create(userId));
    }

    /** 最近会话列表 */
    @GetMapping
    public Result<List<AgentSessionVO>> list(@RequestParam(required = false, defaultValue = "20") Integer limit,
                                             HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(sessionService.list(userId, limit == null ? 20 : limit));
    }

    /** 会话消息回放 */
    @GetMapping("/{sessionId}/messages")
    public Result<List<AgentMessageVO>> messages(@PathVariable String sessionId,
                                                 @RequestParam(required = false, defaultValue = "200") Integer limit,
                                                 HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(sessionService.messages(userId, sessionId, limit == null ? 200 : limit));
    }

    /** 重命名 */
    @PatchMapping("/{sessionId}")
    public Result<AgentSessionVO> rename(@PathVariable String sessionId,
                                         @RequestBody Map<String, String> body,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        String title = body == null ? null : body.get("title");
        return Result.success(sessionService.rename(userId, sessionId, title));
    }

    /** 删除会话 */
    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        sessionService.delete(userId, sessionId);
        return Result.success(null);
    }
}
