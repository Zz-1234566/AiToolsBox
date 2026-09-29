package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收藏（工具 / 提示词），对应 sys_user_favorite。
 */
@Data
@TableName("sys_user_favorite")
public class UserFavorite implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** tool 工具 / prompt 提示词 */
    private String targetType;

    /** tool: tool_code；prompt: prompt_id */
    private String targetId;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
