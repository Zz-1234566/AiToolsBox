package com.example.aitools.service;

/**
 * 邮件发送服务接口。
 * 通过接口抽象，未来切换邮件服务商（QQ SMTP → 腾讯云 SES → 阿里云邮件推送）时，
 * 只需新增实现类，业务层代码零修改。
 */
public interface MailService {

    /**
     * 发送验证码邮件
     * @param to 收件人邮箱
     * @param code 验证码
     * @param expireMinutes 有效期（分钟）
     */
    void sendVerifyCode(String to, String code, int expireMinutes);

    /**
     * 发送简单文本邮件
     * @param to 收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    void sendSimpleMail(String to, String subject, String content);
}
