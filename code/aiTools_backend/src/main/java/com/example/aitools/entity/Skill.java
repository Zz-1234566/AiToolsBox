package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 技能表（sys_skill）：文件是内容源，表存运营态 */
@Data
@TableName("sys_skill")
public class Skill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String skillCode;
    private String title;
    private String subtitle;
    private String icon;
    private String triggerDesc;
    private String category;
    private String tools;
    private String outputs;
    private String body;
    private String draftBody;
    private String publishedBody;
    private String draftHash;
    private String publishedHash;
    private java.time.LocalDateTime draftUpdatedAt;
    private java.time.LocalDateTime publishedAt;
    private Long publishedBy;
    private String bodyHash;
    private String version;
    private String scope;
    private Long ownerId;
    private String agentCode;
    private String source;
    private Integer status;
    private Integer sortNo;
    private Integer useCount;
    private String extra;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer dr;
}
