package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.FavoriteRequest;
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

    /** 收藏列表（可选按类型过滤：tool / prompt），targetType 走 query 便于 GET 调用 */
    @GetMapping("/list")
    public Result<List<FavoriteVO>> list(@RequestParam(required = false) String targetType,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(favoriteService.list(userId, targetType));
    }

    /** 添加收藏（JSON body，与前端 request() 封装一致） */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody FavoriteRequest body, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        favoriteService.add(userId, body.getTargetType(), body.getTargetId());
        return Result.success("收藏成功", null);
    }

    /** 取消收藏（JSON body） */
    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestBody FavoriteRequest body, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        favoriteService.remove(userId, body.getTargetType(), body.getTargetId());
        return Result.success("已取消收藏", null);
    }
}
