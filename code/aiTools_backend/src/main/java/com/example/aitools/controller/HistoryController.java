package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.service.HistoryService;
import com.example.aitools.vo.HistoryVO;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;
    private final AuthUtil authUtil;

    /**
     * 查询指定用户在指定工具的历史记录（默认 Constants.HISTORY_LIST_DEFAULT_LIMIT 条）
     * @param userId  用户 id（账号，必填）
     * @param aiCode  可选，按工具编码过滤（如 work-summary / meeting-minutes）
     * @param limit   可选，返回条数上限（1 <= limit <= 50，默认 Constants.HISTORY_LIST_DEFAULT_LIMIT）
     */
    @GetMapping("/list")
    public Result<List<HistoryVO>> list(
            @RequestParam Long userId,
            @RequestParam(required = false) String aiCode,
            @RequestParam(required = false) Integer limit) {
        int n = (limit == null || limit <= 0) ? Constants.HISTORY_LIST_DEFAULT_LIMIT
                : Math.min(limit, 50); // 硬上限 50 防滥用
        return Result.success(historyService.listRecent(userId, aiCode, n));
    }

    /**
     * 删除历史记录
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        historyService.delete(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 清空历史记录（全部）
     */
    @DeleteMapping("/clear")
    public Result<Void> clearAll(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        historyService.clearAll(userId);
        return Result.success("清空成功", null);
    }

}
