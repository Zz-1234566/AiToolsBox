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

    @TableLogic
    private Integer dr;
}
