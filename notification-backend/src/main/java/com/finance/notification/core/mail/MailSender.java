package com.finance.notification.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class MailSender {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${notification.from-address:noreply@finance.local}")
    private String fromAddress;

    @Async
    public void send(String to, String subject, String templateName, Map<String, Object> model, String theme) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(renderHtml(templateName, model, theme), true);
            mailSender.send(message);
            log.info("Email sent template={} to={} subject={} theme={}", templateName, to, subject, theme);
        } catch (MessagingException ex) {
            log.error("Failed to send email template={} to={}: {}", templateName, to, ex.getMessage(), ex);
        }
    }

    private String renderHtml(String templateName, Map<String, Object> model, String theme) {
        Context ctx = new Context();
        ctx.setVariables(model);
        ctx.setVariable("theme", theme);
        return templateEngine.process("email/" + templateName, ctx);
    }
}
