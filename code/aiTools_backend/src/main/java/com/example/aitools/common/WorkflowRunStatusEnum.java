package com.example.aitools.common;

import java.util.Arrays;

/**
 * 工作流运行状态枚举（与 sys_workflow_run.status 1:1）。
 * <p>
 * 设计对齐 {@link BatchTaskStatusEnum}（0-4 + 终止判定），额外增加「已取消」。
 */
public enum WorkflowRunStatusEnum {

    PENDING(0, "待运行", false),
    RUNNING(1, "运行中", false),
    COMPLETED(2, "全部成功", true),
    PARTIAL(3, "部分失败", true),
    FAILED(4, "全部失败", true),
    CANCELLED(5, "已取消", true);

    private final int code;
    private final String label;
    private final boolean terminal;

    WorkflowRunStatusEnum(int code, String label, boolean terminal) {
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
    public static WorkflowRunStatusEnum of(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工作流运行状态: " + code));
    }

    /** 按 code 取中文 label，未知返回 null */
    public static String labelOf(int code) {
        for (WorkflowRunStatusEnum e : values()) {
            if (e.code == code) return e.label;
        }
        return null;
    }
}
