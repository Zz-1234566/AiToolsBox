package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.service.FavoriteService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.FavoriteVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户收藏 Controller（工具 / 提示词）。
 * <p>只做：鉴权 + 调 service + 返回包装结果。</p>
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuthUtil authUtil;

    /** 收藏列表（可选按类型过滤：tool / prompt） */
    @GetMapping("/list")
    public Result<List<FavoriteVO>> list(@RequestParam(required = false) String targetType,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(favoriteService.list(userId, targetType));
    }

    /** 添加收藏 */
    @PostMapping("/add")
    public Result<Void> add(@RequestParam String targetType, @RequestParam String targetId,
                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        favoriteService.add(userId, targetType, targetId);
        return Result.success("收藏成功", null);
    }

    /** 取消收藏 */
    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestParam String targetType, @RequestParam String targetId,
                               HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        favoriteService.remove(userId, targetType, targetId);
        return Result.success("已取消收藏", null);
    }
}
