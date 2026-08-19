package com.example.aitools.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateProfileRequest implements Serializable {

    /** 用户名（可选，不传则不修改） */
    private String username;

    /** 头像URL（可选，不传则不修改） */
    private String avatar;
}
