package com.example.aitools.agent.skill;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能注册表：启动时扫描 classpath:skills/&#42;/SKILL.md，解析 frontmatter + 正文。
 * <p>
 * 为什么用技能而不是继续加工具：工具是「一次调用」，技能是「流程与写作规范」。
 * 长篇文档生成这类任务需要多步编排，写成工具只能得到一个质量很差的单次产物。
 * <p>
 * 渐进式披露：系统提示里只放 name + title + when（几十字），
 * 正文按需由 read-skill 工具加载，避免上下文被大量流程文档占满。
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    /** 草稿预览覆盖：code → {body, expireAt}，试用时临时生效，不影响线上 */
    private final Map<String, Object[]> preview = new java.util.concurrent.ConcurrentHashMap<>();

    /** 设置/清除草稿预览（试用结束后必须清除） */
    public void setPreview(Long userId, String code, String body) {
        String key = previewKey(userId, code);
        if (body == null) {
            preview.remove(key);
        } else {
            preview.put(key, new Object[]{body, System.currentTimeMillis() + 120_000L});
        }
    }

    public void clearPreview(Long userId) {
        preview.keySet().removeIf(k -> k.startsWith(userId + ":"));
    }

    private static String previewKey(Long userId, String code) {
        return (userId == null ? "0" : userId) + ":" + (code == null ? "" : code);
    }

    public void clearPreview() {
        preview.clear();
    }
    private final com.example.aitools.mapper.SkillMapper skillMapper;

    public SkillRegistry(com.example.aitools.mapper.SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @PostConstruct
    public void load() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:skills/*/SKILL.md");
            for (Resource r : resources) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    Skill s = parse(sb.toString());
                    if (s != null) {
                        skills.put(s.getName(), s);
                    }
                } catch (Exception e) {
                    log.warn("[skill] 解析失败 {}: {}", r.getFilename(), e.getMessage());
                }
            }
            log.info("[skill] 加载 {} 个技能：{}", skills.size(), skills.keySet());
            syncToDb();
        } catch (Exception e) {
            log.warn("[skill] 扫描失败: {}", e.getMessage());
        }
    }

    private Skill parse(String raw) {
        if (raw == null || !raw.startsWith("---")) {
            return null;
        }
        int end = raw.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        String fm = raw.substring(3, end);
        String body = raw.substring(raw.indexOf('\n', end + 1) + 1);

        Skill s = new Skill();
        s.setBody(body.trim());
        for (String line : fm.split("\n")) {
            int i = line.indexOf(':');
            if (i <= 0) {
                continue;
            }
            String k = line.substring(0, i).trim();
            String v = line.substring(i + 1).trim();
            switch (k) {
                case "name" -> s.setName(v);
                case "title" -> s.setTitle(v);
                case "icon" -> s.setIcon(v);
                case "version" -> s.setVersion(v);
                case "when" -> s.setWhen(v);
                case "tools" -> s.setTools(v);
                case "outputs" -> s.setOutputs(v);
                default -> { }
            }
        }
        return s.getName() == null || s.getName().isBlank() ? null : s;
    }

    /**
     * 文件 → 库同步：按 skill_code upsert，只有正文 hash 变化才更新内容；
     * 运营态字段（status/sort_no/scope）以库为准，不被文件覆盖。
     */
    public void syncToDb() {
        int inserted = 0, updated = 0;
        for (Skill s : skills.values()) {
            try {
                String hash = md5(s.getBody());
                var exist = skillMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aitools.entity.Skill>()
                                .eq(com.example.aitools.entity.Skill::getSkillCode, s.getName()));
                if (exist == null) {
                    com.example.aitools.entity.Skill e = new com.example.aitools.entity.Skill();
                    e.setSkillCode(s.getName());
                    e.setTitle(s.getTitle());
                    e.setIcon(s.getIcon());
                    e.setTriggerDesc(s.getWhen());
                    e.setTools(s.getTools());
                    e.setOutputs(s.getOutputs());
                    e.setVersion(s.getVersion());
                    e.setBody(s.getBody());
                    e.setBodyHash(hash);
                    e.setDraftBody(s.getBody());
                    e.setPublishedBody(s.getBody());
                    e.setDraftHash(hash);
                    e.setPublishedHash(hash);
                    e.setPublishedAt(java.time.LocalDateTime.now());
                    e.setDraftUpdatedAt(java.time.LocalDateTime.now());
                    e.setScope("system");
                    e.setSource("file");
                    e.setStatus(1);
                    e.setSortNo(100);
                    e.setUseCount(0);
                    e.setDr(0);
                    skillMapper.insert(e);
                    inserted++;
                } else if (!java.util.Objects.equals(exist.getBodyHash(), hash) && !isManaged(exist)) {
                    exist.setTitle(s.getTitle());
                    exist.setIcon(s.getIcon());
                    exist.setTriggerDesc(s.getWhen());
                    exist.setTools(s.getTools());
                    exist.setOutputs(s.getOutputs());
                    exist.setVersion(s.getVersion());
                    exist.setBody(s.getBody());
                    exist.setBodyHash(hash);
                    skillMapper.updateById(exist);
                    updated++;
                }
            } catch (Exception e) {
                log.warn("[skill] 同步失败 {}: {}", s.getName(), e.getMessage());
            }
        }
        log.info("[skill] 同步完成：新增 {}，更新 {}，共 {} 个", inserted, updated, skills.size());
    }

    /**
     * 该技能是否已在后台被接管（手工创建或有运营字段改动）：
     * 这类记录不再被文件覆盖，避免后台改完重启后丢失。
     */
    private boolean isManaged(com.example.aitools.entity.Skill e) {
        // 运营改过（草稿与线上不一致）或手工创建 → 文件不再覆盖
        if ("manual".equals(e.getSource())) {
            return true;
        }
        String draft = e.getDraftHash();
        String pub = e.getPublishedHash();
        return draft != null && !draft.equals(pub);
    }

    /** 记录一次技能被读取 */
    public void markUsed(String name) {
        try {
            var e = skillMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aitools.entity.Skill>()
                            .eq(com.example.aitools.entity.Skill::getSkillCode, name));
            if (e != null) {
                e.setUseCount((e.getUseCount() == null ? 0 : e.getUseCount()) + 1);
                skillMapper.updateById(e);
            }
        } catch (Exception ignore) { }
    }

    /** 启用中的技能（库里禁用后不进系统提示） */
    public List<Skill> enabled() {
        java.util.Set<String> off = new java.util.HashSet<>();
        try {
            for (com.example.aitools.entity.Skill e : skillMapper.selectList(null)) {
                if (e.getStatus() != null && e.getStatus() == 0) {
                    off.add(e.getSkillCode());
                }
            }
        } catch (Exception ignore) { }
        List<Skill> out = new ArrayList<>();
        for (Skill s : skills.values()) {
            if (!off.contains(s.getName())) {
                out.add(s);
            }
        }
        return out;
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

    public List<Skill> all() {
        return new ArrayList<>(skills.values());
    }

    public Skill get(String name) {
        return get(name, null);
    }

    /** 带用户的读取：只命中自己设置过的草稿预览，避免多人试用互相串味 */
    public Skill get(String name, Long userId) {
        Skill s = name == null ? null : skills.get(name.trim());
        String code = name == null ? null : name.trim();
        Object[] pv = code == null ? null : preview.get(previewKey(userId, code));
        if (pv != null && (long) pv[1] > System.currentTimeMillis() && s != null) {
            Skill copy = new Skill();
            copy.setName(s.getName());
            copy.setTitle(s.getTitle());
            copy.setIcon(s.getIcon());
            copy.setWhen(s.getWhen());
            copy.setTools(s.getTools());
            copy.setOutputs(s.getOutputs());
            copy.setBody(String.valueOf(pv[0]));
            return copy;
        }
        if (s == null) {
            return null;
        }
        // 线上内容以库为准（运营发布后立即生效，无需重启）
        try {
            var e = skillMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aitools.entity.Skill>()
                            .eq(com.example.aitools.entity.Skill::getSkillCode, s.getName()));
            if (e != null && e.getPublishedBody() != null && !e.getPublishedBody().isBlank()) {
                Skill copy = new Skill();
                copy.setName(s.getName());
                copy.setTitle(s.getTitle());
                copy.setIcon(s.getIcon());
                copy.setWhen(s.getWhen());
                copy.setTools(s.getTools());
                copy.setOutputs(s.getOutputs());
                copy.setVersion(e.getVersion());
                copy.setBody(e.getPublishedBody());
                return copy;
            }
        } catch (Exception ignore) { }
        return s;
    }

    public String indexText() {
        if (skills.isEmpty()) {
            return "（当前没有可用技能）";
        }
        StringBuilder sb = new StringBuilder();
        for (Skill s : enabled()) {
            sb.append("- ").append(s.getName()).append("（").append(s.getTitle()).append("）：")
                    .append(s.getWhen()).append('\n');
        }
        return sb.toString();
    }

    @Data
    public static class Skill {
        private String name;
        private String title;
        private String icon;
        private String version;
        private String when;
        private String tools;
        private String outputs;
        private String body;
    }
}
