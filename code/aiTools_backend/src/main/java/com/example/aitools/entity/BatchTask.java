package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量任务实体（多文件上传 B2 方案用）
 * 状态：0=PENDING 1=RUNNING 2=COMPLETED 3=PARTIAL 4=FAILED
 */
@Data
@TableName("sys_batch_task")
public class BatchTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外 batchId（UUID） */
    private String batchId;

    private Long userId;

    private String toolCode;

    private Integer fileCount;

    private Integer successCount;

    private Integer failCount;

    /** 已处理文件数（成功+失败，前端轮询 since 增量用） */
    private Integer processedIndex;

    /** 0=PENDING 1=RUNNING 2=COMPLETED 3=PARTIAL 4=FAILED */
    private Integer status;

    /** 汇总结果（JSON 数组字符串：[{"fileName":"...","status":"OK|FAIL","output":"..."}]） */
    private String resultSummary;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime finishedAt;

    private Integer dr;
}
