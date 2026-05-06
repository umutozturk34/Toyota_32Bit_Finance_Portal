package com.finance.notification.core.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    void create_buildsNotificationWithProvidedFields() {
        Map<String, Object> meta = Map.of("alertId", 7L);

        Notification n = Notification.create("user-1", NotificationType.PRICE_ALERT_FIRED,
                "BTC alert", "BTC crossed 100k", meta, null);

        assertThat(n.getUserSub()).isEqualTo("user-1");
        assertThat(n.getType()).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
        assertThat(n.getTitle()).isEqualTo("BTC alert");
        assertThat(n.getBody()).isEqualTo("BTC crossed 100k");
        assertThat(n.getMetadata()).isEqualTo(meta);
        assertThat(n.getReadAt()).isNull();
    }

    @Test
    void isUnread_returnsTrueWhenReadAtNull() {
        Notification n = Notification.create("u", NotificationType.SYSTEM, "t", "b", Map.of(), null);

        boolean result = n.isUnread();

        assertThat(result).isTrue();
    }

    @Test
    void markRead_setsReadAtOnce() {
        Notification n = Notification.create("u", NotificationType.SYSTEM, "t", "b", Map.of(), null);

        n.markRead();
        LocalDateTime first = n.getReadAt();
        n.markRead();

        assertThat(first).isNotNull();
        assertThat(n.getReadAt()).isEqualTo(first);
        assertThat(n.isUnread()).isFalse();
    }

    @Test
    void isExpired_returnsTrueWhenExpiresAtInPast() {
        Notification n = Notification.create("u", NotificationType.SYSTEM, "t", "b", Map.of(),
                LocalDateTime.now().minusMinutes(1));

        boolean result = n.isExpired();

        assertThat(result).isTrue();
    }

    @Test
    void isExpired_returnsFalseWhenExpiresAtNull() {
        Notification n = Notification.create("u", NotificationType.SYSTEM, "t", "b", Map.of(), null);

        boolean result = n.isExpired();

        assertThat(result).isFalse();
    }

    @Test
    void isExpired_returnsFalseWhenExpiresAtInFuture() {
        Notification n = Notification.create("u", NotificationType.SYSTEM, "t", "b", Map.of(),
                LocalDateTime.now().plusHours(1));

        boolean result = n.isExpired();

        assertThat(result).isFalse();
    }

    @Test
    void belongsTo_matchesOnlySameUserSub() {
        Notification n = Notification.create("user-1", NotificationType.SYSTEM, "t", "b", Map.of(), null);

        assertThat(n.belongsTo("user-1")).isTrue();
        assertThat(n.belongsTo("other")).isFalse();
    }
}
