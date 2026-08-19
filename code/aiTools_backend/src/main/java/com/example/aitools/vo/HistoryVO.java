package com.example.aitools.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史记录出参（主表 + 明细 + 文件 + 工具名）
 */
@Data
public class HistoryVO {

    private Long id;

    /** 用户ID */
    private Long userId;

    /** 工具ID */
    private Long toolId;

    /** 模型ID */
    private Long modelId;

    /** AI动作编码 */
    private String aiCode;

    /** 工具名称（联查工具表） */
    private String toolName;

    /** 状态：1成功 0失败 */
    private Integer status;

    /** 耗时毫秒 */
    private Integer duration;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 输入内容（从明细取） */
    private String inputContent;

    /** 输出结果（从明细取） */
    private String outputContent;

    /** 错误信息（失败时） */
    private String errorMsg;

    /** 涉及文件列表 */
    private List<HistoryFileVO> files;
}
