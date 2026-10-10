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
     * 查询当前登录用户在指定工具下的历史记录（默认 Constants.HISTORY_LIST_DEFAULT_LIMIT 条）
     * <p>
     * 安全：查询范围<b>一律以登录态为准</b>，不接受前端传入的 userId，
     * 否则登录用户传别人的 userId 即可越权读取他人历史（含输入/输出原文与文件）。
     *
     * @param userId  <b>已忽略，仅为兼容前端保留</b>，真实用户 id 取自登录态
     * @param aiCode  可选，按工具编码过滤（如 work-summary / meeting-minutes）
     * @param limit   可选，返回条数上限（1 &lt;= limit &lt;= 50，默认 Constants.HISTORY_LIST_DEFAULT_LIMIT）
     * @param offset  可选，跳过条数（从 0 开始），供前端滚动加载更多使用；默认 0
     */
    @GetMapping("/list")
    public Result<List<HistoryVO>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String aiCode,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        // 覆盖而非校验：直接丢弃前端传值，登录态缺失时 AuthUtil 抛 401
        Long currentUserId = authUtil.getUserIdFromRequest(request);
        int n = (limit == null || limit <= 0) ? Constants.HISTORY_LIST_DEFAULT_LIMIT
                : Math.min(limit, 50); // 硬上限 50 防滥用
        int off = (offset == null || offset < 0) ? 0 : offset;
        return Result.success(historyService.listRecent(currentUserId, aiCode, n, off));
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
