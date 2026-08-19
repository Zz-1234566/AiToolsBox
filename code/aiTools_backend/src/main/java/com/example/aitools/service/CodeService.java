package com.example.aitools.service;

import java.util.function.Consumer;

/**
 * 验证码服务接口
 * 实现类：service/impl/CodeServiceImpl.java
 * key 格式：verify:code:{type}:{target}
 * value：6 位数字验证码
 * TTL：5 分钟自动过期
 */
public interface CodeService {

    /**
     * 生成验证码并发送（含限流检查）
     * @param type 场景类型（register / reset-password）
     * @param target 目标（邮箱）
     * @param sendAction 实际发送动作（发邮件）
     * @return 是否允许发送（false=触发限流）
     */
    boolean generateAndSend(String type, String target, Consumer<String> sendAction);

    /**
     * 校验验证码
     * @param type 场景类型
     * @param target 目标（邮箱）
     * @param inputCode 用户输入验证码
     * @return 是否匹配
     */
    boolean verify(String type, String target, String inputCode);

    /**
     * 校验通过后删除验证码（一次性使用）
     */
    void delete(String type, String target);
}
