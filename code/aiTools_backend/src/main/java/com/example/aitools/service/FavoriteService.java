package com.example.aitools.service;

import com.example.aitools.vo.FavoriteVO;

import java.util.List;

/**
 * 用户收藏服务（工具 / 提示词）。
 * <p>当前用于「我的收藏」页面展示；增删为最小实现。</p>
 */
public interface FavoriteService {

    /** 收藏列表（组装工具 / 提示词明细） */
    List<FavoriteVO> list(Long userId, String targetType);

    /** 添加收藏（已存在则忽略，幂等） */
    void add(Long userId, String targetType, String targetId);

    /** 取消收藏 */
    void remove(Long userId, String targetType, String targetId);
}
