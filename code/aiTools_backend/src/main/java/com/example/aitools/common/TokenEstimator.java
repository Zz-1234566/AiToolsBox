package com.example.aitools.common;

import java.util.List;
import java.util.Map;

/**
 * 上下文 token 的**粗略估算**（不依赖模型 tokenizer）。
 * <p>
 * <b>为什么需要</b>：Agent 循环里上下文是一路追加的，撞到模型窗口会直接 400。
 * 但在接入 tokenizer 之前，先能"量个大概"就能判断是否接近上限：
 * 中文按 1 字 ≈ 1 token，英文/符号按 4 字符 ≈ 1 token（业界常用经验值，误差约 ±15%）。
 * <p>
 * <b>用途边界</b>：只用于日志与阈值告警，**不要**用于精确计费或硬性截断决策。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * 估算一段文本的 token 数。
     *
     * @param text 文本（可空）
     * @return 估算 token 数（空文本返回 0）
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        // 中文：1:1；其余：4 字符 ≈ 1 token
        return cjk + (other + 3) / 4;
    }

    /**
     * 估算一轮对话消息列表的 token 数（含每条消息的固定开销）。
     *
     * @param messages OpenAI 兼容格式的消息列表
     * @return 估算 token 数
     */
    public static int estimateMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        // 每条消息约有 4 token 的角色/分隔开销（OpenAI 文档口径）
        int total = 0;
        for (Map<String, String> m : messages) {
            total += 4 + estimate(m == null ? null : m.get("content"));
        }
        return total;
    }

    /** 是否中日韩字符（含全角标点） */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 基本汉字
                || (c >= 0x3400 && c <= 0x4DBF)  // 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF)  // 兼容汉字
                || (c >= 0x3000 && c <= 0x303F)  // 中文标点
                || (c >= 0xFF00 && c <= 0xFFEF); // 全角字符
    }
}
