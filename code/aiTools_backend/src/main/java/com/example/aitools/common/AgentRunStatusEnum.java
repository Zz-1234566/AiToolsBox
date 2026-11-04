package com.example.aitools.common;

import java.util.Arrays;

/**
 * 智能体运行状态枚举（与 sys_agent_run.status 1:1）。
 *
 * <p>比 {@link WorkflowRunStatusEnum} 少一个「部分失败」——
 * 智能体是「一条链路走到底」，没有分支，所以只有成功 / 失败 / 取消三种终态。
 */
public enum AgentRunStatusEnum {

    PENDING(0, "待运行", false),
    RUNNING(1, "运行中", false),
    COMPLETED(2, "已完成", true),
    FAILED(3, "失败", true),
    CANCELLED(4, "已取消", true);

    private final int code;
    private final String label;
    private final boolean terminal;

    AgentRunStatusEnum(int code, String label, boolean terminal) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否终态（不再推进） */
    public boolean isTerminal() {
        return terminal;
    }

    /** 按 code 解析，未知抛异常（避免静默返回错误状态） */
    public static AgentRunStatusEnum of(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知智能体运行状态: " + code));
    }

    /** 按 code 取中文 label，未知返回 null */
    public static String labelOf(int code) {
        for (AgentRunStatusEnum e : values()) {
            if (e.code == code) {
                return e.label;
            }
        }
        return null;
    }
}
