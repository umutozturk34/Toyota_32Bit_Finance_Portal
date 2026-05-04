package com.finance.notification.messaging.security;

import com.finance.common.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingTierTest {

    private final MessagingUserTier userTier = new MessagingUserTier();
    private final MessagingAdminTier adminTier = new MessagingAdminTier();

    @ParameterizedTest
    @CsvSource({
            "/api/v1/messages, POST, true",
            "/api/v1/messages, GET, false",
            "/api/v1/messages/123/read, POST, false",
            "/api/v1/admin/messages, POST, false",
            "/api/v1/notifications, POST, false"
    })
    void userTier_matchesExactPostOnSendEndpoint(String path, String method, boolean expected) {
        boolean result = userTier.matches(path, method);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/admin/messages, POST, true",
            "/api/v1/admin/messages, GET, false",
            "/api/v1/admin/messages/inbox, POST, true",
            "/api/v1/messages, POST, false",
            "/api/v1/admin/trigger, POST, false"
    })
    void adminTier_matchesAnyPostUnderAdminMessages(String path, String method, boolean expected) {
        boolean result = adminTier.matches(path, method);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void userTier_bandwidthHonorsConfiguredLimit() {
        AppProperties.RateLimit rl = new AppProperties.RateLimit();
        rl.setMessagingUserLimit(7);

        Bandwidth bw = userTier.toBandwidth(rl);

        assertThat(bw.getCapacity()).isEqualTo(7);
    }

    @Test
    void adminTier_bandwidthHonorsConfiguredLimit() {
        AppProperties.RateLimit rl = new AppProperties.RateLimit();
        rl.setMessagingAdminLimit(42);

        Bandwidth bw = adminTier.toBandwidth(rl);

        assertThat(bw.getCapacity()).isEqualTo(42);
    }
}
