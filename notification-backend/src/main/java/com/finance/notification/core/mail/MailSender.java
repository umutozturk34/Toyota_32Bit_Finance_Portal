package com.finance.notification.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import com.finance.notification.config.NotificationAsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Log4j2
@Component("notificationMailSender")
@RequiredArgsConstructor
public class MailSender {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${notification.from-address:noreply@finance.local}")
    private String fromAddress;

    @Async(NotificationAsyncConfig.MAIL_EXECUTOR)
    public void send(String to, String subject, String templateName, Map<String, Object> model, String theme, Locale locale) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(renderHtml(templateName, model, theme, locale), true);
            mailSender.send(message);
            log.info("Email sent template={} to={} subject={} theme={} locale={}", templateName, to, subject, theme, locale);
        } catch (MessagingException ex) {
            log.error("Failed to send email template={} to={}: {}", templateName, to, ex.getMessage(), ex);
        }
    }

    private String renderHtml(String templateName, Map<String, Object> model, String theme, Locale locale) {
        Context ctx = new Context(locale);
        ctx.setVariables(model);
        ctx.setVariable("theme", theme);
        return templateEngine.process("email/" + templateName, ctx);
    }
}
