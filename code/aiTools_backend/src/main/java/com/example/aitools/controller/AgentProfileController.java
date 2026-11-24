package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.entity.UserProfile;
import com.example.aitools.mapper.UserProfileMapper;
import com.example.aitools.service.AgentProfileService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AgentProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户偏好 Controller（词条化画像）。
 * <p>
 * 只做收参 / 鉴权 / 交给 Service。
 */
@RestController
@RequestMapping("/api/agent/profile")
@RequiredArgsConstructor
public class AgentProfileController {

    private final AgentProfileService profileService;
    private final UserProfileMapper userProfileMapper;
    private final AuthUtil authUtil;

    /** 读取偏好词条与画像摘要 */
    @GetMapping
    public Result<AgentProfileVO> get(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        AgentProfileVO vo = new AgentProfileVO();
        vo.setPreferences(profileService.getPreferences(userId));
        UserProfile p = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId).last("LIMIT 1"));
        vo.setProfileText(p == null ? "" : p.getProfileText());
        return Result.success(vo);
    }

    /** 保存偏好词条（整体覆盖；这些键不会再被 AI 覆盖） */
    @PutMapping
    public Result<Void> save(@RequestBody Map<String, Map<String, String>> body,
                             HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        profileService.savePreferences(userId, body == null ? null : body.get("preferences"));
        return Result.success(null);
    }
}
