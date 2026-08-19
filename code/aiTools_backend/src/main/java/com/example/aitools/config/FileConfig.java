package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileConfig {

    /** 本地文件存储目录（相对或绝对路径） */
    private String uploadDir = "uploads";
}
