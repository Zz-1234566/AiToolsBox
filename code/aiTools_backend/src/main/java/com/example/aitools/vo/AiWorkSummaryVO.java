package com.example.aitools.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 工作总结出参
 */
@Data
@AllArgsConstructor
public class AiWorkSummaryVO {

    /** AI 生成的整理结果 */
    private String result;
}
