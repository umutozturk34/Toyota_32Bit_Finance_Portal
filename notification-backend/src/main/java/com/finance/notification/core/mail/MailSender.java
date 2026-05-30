package com.finance.notification.core.mail;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a Thymeleaf email template (theme/locale-aware) and sends it via SMTP. Wrapped in a
 * circuit breaker so a failing mail server fails fast, and all mail/messaging errors are rethrown as
 * {@link MailDispatchException} for the consumer's retry handling.
 */
@Log4j2
@Component("notificationMailSender")
@RequiredArgsConstructor
public class MailSender {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${notification.from-address:noreply@finance.local}")
    private String fromAddress;

    @CircuitBreaker(name = "smtp")
    public void sendBlocking(String to, String subject, String templateName,
                             Map<String, Object> model, String theme, Locale locale) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(sanitizeHeader(subject));
            helper.setText(renderHtml(templateName, model, theme, locale), true);
            mailSender.send(message);
            log.info("Email sent template={} to={} theme={} locale={}", templateName, to, theme, locale);
        } catch (MessagingException ex) {
            throw new MailDispatchException(ex.getMessage(), ex);
        } catch (MailException ex) {
            throw new MailDispatchException(ex.getMessage(), ex);
        }
    }

    private static String sanitizeHeader(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n\\t]", " ").trim();
    }

    private String renderHtml(String templateName, Map<String, Object> model, String theme, Locale locale) {
        Context ctx = new Context(locale);
        ctx.setVariables(model);
        ctx.setVariable("theme", theme);
        return templateEngine.process("email/" + templateName, ctx);
    }

    /** Unchecked wrapper for SMTP/templating failures, signalling the send should be retried. */
    public static class MailDispatchException extends RuntimeException {
        public MailDispatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
