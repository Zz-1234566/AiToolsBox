package com.example.aitools.controller;

import com.example.aitools.agent.skill.SkillRegistry;
import com.example.aitools.common.Result;
import com.example.aitools.entity.Skill;
import com.example.aitools.mapper.SkillMapper;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能接口：列表（前端展示）+ 热重载（管理端）。
 * <p>
 * 新增技能不需要改这里：放一个目录 + 调 reload（或重启）即可。
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillMapper skillMapper;
    private final SkillRegistry skillRegistry;
    private final AuthUtil authUtil;
    private final com.example.aitools.agent.AgentService agentService;
    private final com.example.aitools.service.AgentSessionService agentSessionService;

    /** 启用中的技能列表（含图标与适用场景） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        List<Skill> rows = skillMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Skill>()
                        .eq(Skill::getStatus, 1)
                        .orderByAsc(Skill::getSortNo));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Skill s : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getSkillCode());
            m.put("title", s.getTitle());
            m.put("icon", s.getIcon());
            m.put("when", s.getTriggerDesc());
            m.put("category", s.getCategory());
            m.put("outputs", s.getOutputs());
            m.put("version", s.getVersion());
            m.put("useCount", s.getUseCount());
            out.add(m);
        }
        return Result.success(out);
    }

    /** 热重载：重扫 classpath:skills 并同步到库（管理端用） */
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload(HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        skillRegistry.load();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", skillRegistry.all().size());
        m.put("codes", skillRegistry.all().stream().map(SkillRegistry.Skill::getName).toList());
        return Result.success(m);
    }

    /** 技能详情：线上内容 + 草稿 + 是否有未发布改动 */
    @GetMapping("/{code}")
    public Result<Map<String, Object>> detail(@PathVariable String code, HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        Skill s = findByCode(code);
        if (s == null) {
            return Result.fail(404, "技能不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", s.getSkillCode());
        m.put("title", s.getTitle());
        m.put("icon", s.getIcon());
        m.put("version", s.getVersion());
        m.put("publishedBody", s.getPublishedBody());
        m.put("draftBody", s.getDraftBody());
        m.put("publishedAt", s.getPublishedAt());
        m.put("draftUpdatedAt", s.getDraftUpdatedAt());
        m.put("dirty", s.getDraftHash() != null && !s.getDraftHash().equals(s.getPublishedHash()));
        m.put("source", s.getSource());
        return Result.success(m);
    }

    /** 保存草稿（不生效，需发布） */
    @PutMapping("/{code}/draft")
    public Result<Boolean> saveDraft(@PathVariable String code,
                                     @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        Skill s = findByCode(code);
        if (s == null) {
            return Result.fail(404, "技能不存在");
        }
        String text = body == null ? null : body.get("body");
        if (text == null) {
            return Result.fail(400, "缺少正文");
        }
        s.setDraftBody(text);
        s.setDraftHash(md5(text));
        s.setDraftUpdatedAt(java.time.LocalDateTime.now());
        skillMapper.updateById(s);
        return Result.success(true);
    }

    /** 发布：草稿 → 线上，版本号递增 */
    @PostMapping("/{code}/publish")
    public Result<Map<String, Object>> publish(@PathVariable String code, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        Skill s = findByCode(code);
        if (s == null) {
            return Result.fail(404, "技能不存在");
        }
        if (s.getDraftBody() == null) {
            return Result.fail(400, "没有可发布的草稿");
        }
        s.setPublishedBody(s.getDraftBody());
        s.setPublishedHash(s.getDraftHash() != null ? s.getDraftHash() : md5(s.getDraftBody()));
        s.setPublishedAt(java.time.LocalDateTime.now());
        s.setPublishedBy(userId);
        s.setVersion(bumpVersion(s.getVersion()));
        skillMapper.updateById(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", s.getVersion());
        m.put("publishedAt", s.getPublishedAt());
        return Result.success(m);
    }

    /** 从文件重新导入（覆盖草稿，需前端二次确认） */
    @PostMapping("/{code}/reimport")
    public Result<Boolean> reimport(@PathVariable String code, HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        skillRegistry.load();
        Skill s = findByCode(code);
        var fileSkill = skillRegistry.all().stream()
                .filter(x -> x.getName().equals(code)).findFirst().orElse(null);
        if (s == null || fileSkill == null) {
            return Result.fail(404, "技能或文件不存在");
        }
        s.setDraftBody(fileSkill.getBody());
        s.setDraftHash(md5(fileSkill.getBody()));
        s.setDraftUpdatedAt(java.time.LocalDateTime.now());
        skillMapper.updateById(s);
        return Result.success(true);
    }

    private Skill findByCode(String code) {
        return skillMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Skill>()
                        .eq(Skill::getSkillCode, code));
    }

    private static String bumpVersion(String v) {
        try {
            String[] p = (v == null ? "0.1.0" : v).split("\\.");
            int patch = Integer.parseInt(p[p.length - 1]) + 1;
            p[p.length - 1] = String.valueOf(patch);
            return String.join(".", p);
        } catch (Exception e) {
            return "0.1.1";
        }
    }

    private static String md5(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 放弃修改：草稿回滚为线上版本 */
    @PostMapping("/{code}/discard")
    public Result<Boolean> discard(@PathVariable String code, HttpServletRequest request) {
        authUtil.getUserIdFromRequest(request);
        Skill s = findByCode(code);
        if (s == null) {
            return Result.fail(404, "技能不存在");
        }
        s.setDraftBody(s.getPublishedBody());
        s.setDraftHash(s.getPublishedHash());
        s.setDraftUpdatedAt(java.time.LocalDateTime.now());
        skillMapper.updateById(s);
        return Result.success(true);
    }

    /**
     * 试用草稿：用当前草稿跑一次真实对话，产出不写入正式会话。
     * 返回模型回复 + 若产生了文件则带下载链接。
     */
    @PostMapping("/{code}/try")
    public Result<Map<String, Object>> tryDraft(@PathVariable String code,
                                                @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        Skill s = findByCode(code);
        if (s == null) {
            return Result.fail(404, "技能不存在");
        }
        if (s.getDraftBody() == null || s.getDraftBody().isBlank()) {
            return Result.fail(400, "草稿为空");
        }
        String goal = body == null ? null : String.valueOf(body.get("goal"));
        if (goal == null || goal.isBlank()) {
            goal = "请按这个技能的要求完成一次示例任务，并说明你会怎么做";
        }
        StringBuilder frames = new StringBuilder();
        String sessionId = null;
        try {
            skillRegistry.setPreview(userId, code, s.getDraftBody());
            var session = agentSessionService.create(userId);
            sessionId = session.getSessionId();
            String answer = agentService.run(userId, sessionId, goal, java.util.List.of(), null,
                    false, f -> frames.append(f).append('\n'));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", answer);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[文件:([^\\]]+)\\]").matcher(frames);
            java.util.List<Map<String, Object>> files = new java.util.ArrayList<>();
            while (m.find()) {
                String[] parts = m.group(1).split("\\|", 2);
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("url", parts[0]);
                f.put("name", parts.length > 1 ? parts[1] : "文件");
                files.add(f);
            }
            out.put("files", files);
            return Result.success(out);
        } catch (Exception e) {
            return Result.fail(500, "试用失败：" + e.getMessage());
        } finally {
            skillRegistry.clearPreview(userId);
            if (sessionId != null) {
                try {
                    agentSessionService.delete(userId, sessionId);
                } catch (Exception ignore) { }
            }
        }
    }

}
