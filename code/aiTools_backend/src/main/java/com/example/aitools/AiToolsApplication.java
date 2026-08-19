package com.example.aitools;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.aitools.mapper")
public class AiToolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiToolsApplication.class, args);
    }
}
