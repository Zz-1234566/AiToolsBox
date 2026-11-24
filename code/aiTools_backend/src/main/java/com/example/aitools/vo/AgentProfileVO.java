package com.example.aitools.vo;

import lombok.Data;

import java.util.Map;

/**
 * 用户偏好/画像出参。
 * <p>
 * preferences 是词条（键值对），可直接渲染为标签；profileText 是画像摘要（仅供参考展示）。
 */
@Data
public class AgentProfileVO {

    /** 偏好词条 */
    private Map<String, String> preferences;

    /** 画像摘要（展示用，不注入模型） */
    private String profileText;
}
