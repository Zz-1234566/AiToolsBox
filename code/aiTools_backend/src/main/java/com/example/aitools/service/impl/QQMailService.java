package com.example.aitools.service.impl;

import com.example.aitools.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class QQMailService implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendVerifyCode(String to, String code, int expireMinutes) {
        // 使用 HTML 模板发送验证码邮件
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("【智汇工具箱】验证码");
            helper.setText(buildVerifyCodeHtml(code, expireMinutes), true);
            mailSender.send(message);
            log.info("验证码邮件已发送 to={}, code={}", to, code);
        } catch (Exception e) {
            log.error("验证码邮件发送失败 to={}", to, e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        }
    }

    @Override
    public void sendSimpleMail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("简单邮件已发送 to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("简单邮件发送失败 to={}", to, e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        }
    }

    /**
     * 生成验证码 HTML 模板（黑白灰简约风格）
     */
    private String buildVerifyCodeHtml(String code, int expireMinutes) {
        return "<div style=\"max-width:400px;margin:0 auto;padding:24px;font-family:-apple-system,'PingFang SC',sans-serif;color:#211E1E;background:#F8F8F8;border-radius:16px;\">"
                + "<div style=\"text-align:center;font-size:20px;font-weight:700;margin-bottom:16px;\">智汇工具箱</div>"
                + "<div style=\"background:#FFFFFF;border-radius:12px;padding:32px;text-align:center;\">"
                + "<div style=\"font-size:14px;color:#656363;margin-bottom:16px;\">您的验证码是</div>"
                + "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:#211E1E;margin-bottom:16px;\">" + code + "</div>"
                + "<div style=\"font-size:13px;color:#8E8B8B;\">验证码 " + expireMinutes + " 分钟内有效，请勿泄露给他人</div>"
                + "</div>"
                + "<div style=\"text-align:center;font-size:12px;color:#8E8B8B;margin-top:16px;\">如果不是本人操作，请忽略此邮件</div>"
                + "</div>";
    }
}
