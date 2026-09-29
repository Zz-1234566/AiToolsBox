package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_aitools_history_detail")
public class HistoryDetail implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long historyId;

    private String inputContent;

    private String outputContent;

    private String errorMsg;

    /**
     * 用户当时在格式提示词 textarea 的原始内容（resolve 前）—— 用于历史回填原参数重发
     */
    private String promptFormat;

    /**
     * 用户当时在生成提示词 textarea 的原始内容（resolve 前）—— 用于历史回填原参数重发
     */
    private String promptGenerate;

    @TableLogic
    private Integer dr;
}
