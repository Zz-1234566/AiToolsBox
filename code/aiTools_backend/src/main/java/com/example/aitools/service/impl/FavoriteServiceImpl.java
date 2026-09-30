package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.entity.AiPrompt;
import com.example.aitools.entity.AiTool;
import com.example.aitools.entity.UserFavorite;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.mapper.UserFavoriteMapper;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.FavoriteService;
import com.example.aitools.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏服务实现：收藏记录只存 targetType + targetId，
 * 列表时联查工具表 / 提示词表补全展示字段（收藏页仅展示用途）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final String TYPE_TOOL = "tool";
    private static final String TYPE_PROMPT = "prompt";

    private final UserFavoriteMapper favoriteMapper;
    private final AiToolMapper aiToolMapper;
    private final AiPromptTemplateService aiPromptTemplateService;

    @Override
    public List<FavoriteVO> list(Long userId, String targetType) {
        LambdaQueryWrapper<UserFavorite> w = new LambdaQueryWrapper<>();
        w.eq(UserFavorite::getUserId, userId);
        if (targetType != null && !targetType.isBlank()) {
            w.eq(UserFavorite::getTargetType, targetType);
        }
        w.orderByDesc(UserFavorite::getId);
        List<UserFavorite> rows = favoriteMapper.selectList(w);

        List<FavoriteVO> out = new ArrayList<>();
        for (UserFavorite r : rows) {
            FavoriteVO vo = new FavoriteVO();
            vo.setId(r.getId());
            vo.setTargetType(r.getTargetType());
            vo.setTargetId(r.getTargetId());
            vo.setCreateTime(r.getCreateTime());
            if (TYPE_TOOL.equals(r.getTargetType())) {
                AiTool tool = findTool(r.getTargetId());
                if (tool != null) {
                    vo.setToolCode(tool.getToolCode());
                    vo.setToolName(tool.getToolName());
                    vo.setToolDesc(tool.getDescription());
                    vo.setBelongToolCode(tool.getToolCode());
                }
            } else if (TYPE_PROMPT.equals(r.getTargetType())) {
                AiPrompt p = findPrompt(r.getTargetId());
                if (p != null) {
                    vo.setPromptName(p.getPromptName());
                    vo.setPromptText(p.getPromptContent());
                    vo.setPromptUse(p.getPromptUse());
                    vo.setBelongToolCode(p.getToolCode());
                }
            }
            out.add(vo);
        }
        return out;
    }

    @Override
    public void add(Long userId, String targetType, String targetId) {
        validateType(targetType);
        // 幂等插入：已存在（含被逻辑删除的行）则恢复 dr=0，避免撞唯一索引 uk_user_target
        favoriteMapper.upsertFavorite(userId, targetType, targetId);
        log.info("[favorite] add userId={} type={} target={}", userId, targetType, targetId);
    }

    @Override
    public void remove(Long userId, String targetType, String targetId) {
        LambdaQueryWrapper<UserFavorite> w = new LambdaQueryWrapper<>();
        w.eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getTargetType, targetType)
                .eq(UserFavorite::getTargetId, targetId);
        favoriteMapper.delete(w);
    }

    private void validateType(String targetType) {
        if (!TYPE_TOOL.equals(targetType) && !TYPE_PROMPT.equals(targetType)) {
            throw new BusinessException("收藏类型仅支持 tool / prompt");
        }
    }

    private AiTool findTool(String toolCode) {
        LambdaQueryWrapper<AiTool> w = new LambdaQueryWrapper<>();
        w.eq(AiTool::getToolCode, toolCode).last("LIMIT 1");
        return aiToolMapper.selectOne(w);
    }

    private AiPrompt findPrompt(String promptId) {
        try {
            return aiPromptTemplateService.getById(Long.parseLong(promptId));
        } catch (NumberFormatException e) {
            // promptId 非数字：数据异常，记录以便排查（列表侧会跳过该条补全）
            log.warn("收藏记录 promptId 非法: {}", promptId);
            return null;
        } catch (Exception e) {
            // 提示词已被删除等：属预期情况，但需留痕，避免列表静默残缺却无任何线索
            log.warn("查询收藏提示词失败 promptId={}", promptId, e);
            return null;
        }
    }
}
