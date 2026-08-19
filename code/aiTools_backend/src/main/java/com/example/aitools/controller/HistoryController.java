package com.example.aitools.controller;

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
     * 查询最近历史记录（默认 10 条）
     */
    @GetMapping("/list")
    public Result<List<HistoryVO>> list(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(historyService.listRecent(userId, 10));
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
