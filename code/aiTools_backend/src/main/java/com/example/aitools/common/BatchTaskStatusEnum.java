package com.example.aitools.common;

import lombok.Getter;

/**
 * 批量任务状态枚举（多文件上传 B2 方案用）
 * 状态机：PENDING -> RUNNING -> COMPLETED / PARTIAL / FAILED
 *
 * 设计原则：
 * 1) 数据库 sys_batch_task.status 是 TINYINT，code 持久化为 int
 * 2) 所有"状态码 -> 中文 label"的映射统一在这里
 * 3) 终态判断（completed / partial / failed）用 isTerminal()，不要在 Controller / Service 里手写 if
 */
@Getter
public enum BatchTaskStatusEnum {

    PENDING(0, "待处理", false),
    RUNNING(1, "处理中", false),
    COMPLETED(2, "已完成", true),
    PARTIAL(3, "部分失败", true),
    FAILED(4, "全部失败", true);

    private final int code;
    private final String label;
    private final boolean terminal;

    BatchTaskStatusEnum(int code, String label, boolean terminal) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
    }

    /** int -> enum（找不到返回 null，不要返回 UNKNOWN 让上游困惑） */
    public static BatchTaskStatusEnum of(int code) {
        for (BatchTaskStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    /** 简写：code -> label（找不到返回 "未知"） */
    public static String labelOf(int code) {
        BatchTaskStatusEnum e = of(code);
        return e == null ? "未知" : e.label;
    }
}
