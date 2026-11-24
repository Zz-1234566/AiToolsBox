package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户画像与偏好（sys_user_profile）。
 * <p>
 * preferences 是结构化偏好 JSON（便于程序判断），profileText 是 AI 生成的画像摘要（注入提示词）。
 */
@Data
@TableName("sys_user_profile")
public class UserProfile implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（唯一） */
    private Long userId;

    /** 结构化偏好 JSON */
    private String preferences;

    /** 用户手工维护的偏好键 JSON 数组（AI 抽取不会覆盖这些键） */
    private String manualKeys;

    /** AI 生成的画像摘要（仅展示，不注入模型） */
    private String profileText;

    /** 最近一次产出画像的会话 */
    private String sourceSessionId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}
