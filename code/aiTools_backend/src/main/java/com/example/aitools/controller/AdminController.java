package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.entity.User;
import com.example.aitools.entity.UserProfile;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.UserMapper;
import com.example.aitools.mapper.UserProfileMapper;
import com.example.aitools.utils.AuthUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员接口：用户画像词条（偏好）的查看与修改。
 * <p>
 * 需求：画像词条不做用户端展示，只给管理员查看，管理员可看所有用户并修改。
 * <p>
 * 分层：这里只做鉴权与参数收口，读写直接走 Mapper（单表 CRUD，无业务分支）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String ROLE_ADMIN = "admin";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final AuthUtil authUtil;

    /**
     * 用户画像列表（含词条）。
     *
     * @param keyword 可选，按账号 / 昵称模糊过滤
     */
    @GetMapping("/profiles")
    public Result<List<Map<String, Object>>> listProfiles(
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        requireAdmin(request);

        List<User> users = userMapper.selectList(null);
        Map<Long, UserProfile> profileMap = new LinkedHashMap<>();
        for (UserProfile p : userProfileMapper.selectList(null)) {
            profileMap.put(p.getUserId(), p);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            if (u.getDr() != null && u.getDr() == 1) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String k = keyword.trim().toLowerCase();
                String acc = u.getAccount() == null ? "" : u.getAccount().toLowerCase();
                String name = u.getUsername() == null ? "" : u.getUsername().toLowerCase();
                if (!acc.contains(k) && !name.contains(k)) {
                    continue;
                }
            }
            UserProfile p = profileMap.get(u.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("account", u.getAccount());
            row.put("username", u.getUsername());
            row.put("role", u.getRole());
            row.put("profileText", p == null ? "" : (p.getProfileText() == null ? "" : p.getProfileText()));
            row.put("preferences", p == null ? Map.of() : parseJson(p.getPreferences()));
            row.put("manualKeys", p == null ? List.of() : parseJson(p.getManualKeys()));
            row.put("updateTime", p == null || p.getUpdateTime() == null ? null : p.getUpdateTime().toString());
            out.add(row);
        }
        return Result.success(out);
    }

    /**
     * 修改某用户的画像（摘要 + 词条）。
     *
     * @param userId 目标用户
     * @param body   {profileText?: string, preferences?: object}
     */
    @PutMapping("/profiles/{userId}")
    public Result<Boolean> updateProfile(@PathVariable Long userId,
                                         @RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        requireAdmin(request);
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户不存在");
        }

        UserProfile p = userProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId));
        boolean isNew = p == null;
        if (isNew) {
            p = new UserProfile();
            p.setUserId(userId);
            p.setCreateTime(LocalDateTime.now());
        }
        if (body != null && body.containsKey("profileText")) {
            Object v = body.get("profileText");
            p.setProfileText(v == null ? "" : String.valueOf(v));
        }
        if (body != null && body.containsKey("preferences")) {
            Object v = body.get("preferences");
            try {
                p.setPreferences(v == null ? "{}" : JSON.writeValueAsString(v));
                // 管理员手工改的键记为「手工键」，避免被 AI 抽取覆盖
                if (v instanceof Map<?, ?> m) {
                    List<String> keys = new ArrayList<>();
                    for (Object k : m.keySet()) {
                        keys.add(String.valueOf(k));
                    }
                    p.setManualKeys(JSON.writeValueAsString(keys));
                }
            } catch (Exception e) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "词条格式不正确");
            }
        }
        p.setUpdateTime(LocalDateTime.now());

        if (isNew) {
            userProfileMapper.insert(p);
        } else {
            userProfileMapper.updateById(p);
        }
        log.info("[admin] {} 修改用户 {} 的画像词条", request.getRemoteAddr(), userId);
        return Result.success(true);
    }

    /** 管理员校验（非管理员直接 403） */
    private void requireAdmin(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        User u = userMapper.selectById(userId);
        if (u == null || !ROLE_ADMIN.equalsIgnoreCase(u.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅管理员可访问");
        }
    }

    /** 宽松解析：解析失败当空对象，不影响列表展示 */
    private Object parseJson(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(text, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
