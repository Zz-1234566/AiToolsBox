package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_aitools_history")
public class History implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long toolId;

    private Long modelId;

    private String aiCode;

    private Integer status;

    private Integer duration;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
