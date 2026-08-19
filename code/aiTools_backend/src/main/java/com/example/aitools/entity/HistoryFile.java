package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_aitools_history_file")
public class HistoryFile implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long historyId;

    private String fileId;

    private String fileName;

    private String fileUrl;

    private String fileType;

    private Integer role;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
