package com.finance.notification.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSenderTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private SpringTemplateEngine templateEngine;

    private MailSender sender;
    private MimeMessage message;

    @BeforeEach
    void setUp() {
        sender = new MailSender(javaMailSender, templateEngine);
        ReflectionTestUtils.setField(sender, "fromAddress", "noreply@test.local");
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>body</html>");
    }

    @Test
    void sendBlocking_renderHtml_andSendsMimeMessage() {
        sender.sendBlocking("to@test", "Hello", "welcome", Map.of("name", "Ada"), "dark", Locale.ENGLISH);

        verify(templateEngine).process(eq("email/welcome"), any(Context.class));
        verify(javaMailSender).send(message);
    }

    @Test
    void sendBlocking_stripsHeaderNewlines_fromSubject() throws MessagingException {
        sender.sendBlocking("to@test", "Hi\r\nBcc: x@evil", "welcome", Map.of(), null, Locale.ENGLISH);

        assertThat(message.getSubject()).isEqualTo("Hi  Bcc: x@evil");
    }

    @Test
    void sendBlocking_setsFromAddress_andRecipient() throws MessagingException {
        sender.sendBlocking("user@test", "Subject", "welcome", Map.of(), "light", Locale.ENGLISH);

        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@test.local");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@test");
    }

    @Test
    void sendBlocking_wrapsMailException_asMailDispatchException() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendBlocking(
                "user@test", "Subject", "welcome", Map.of(), "light", Locale.ENGLISH))
                .isInstanceOf(MailSender.MailDispatchException.class)
                .hasMessageContaining("smtp down");
    }
}
