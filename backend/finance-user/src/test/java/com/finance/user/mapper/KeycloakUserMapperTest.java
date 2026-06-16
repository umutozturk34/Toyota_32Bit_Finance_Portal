package com.finance.user.mapper;

import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakUserMapperTest {

    private final KeycloakUserMapper mapper = new KeycloakUserMapperImpl();

    @Test
    void should_copyAllFields_when_keycloakUserIsFullyPopulated() {
        KeycloakUser source = new KeycloakUser(
                "kc-id-1", "umut", "umut@finance.local",
                "Umut", "Ozturk", true, true, 1714521600000L);

        AdminUserResponse response = mapper.toResponse(source, List.of("ADMIN", "USER"));

        assertThat(response.id()).isEqualTo("kc-id-1");
        assertThat(response.username()).isEqualTo("umut");
        assertThat(response.email()).isEqualTo("umut@finance.local");
        assertThat(response.firstName()).isEqualTo("Umut");
        assertThat(response.lastName()).isEqualTo("Ozturk");
        assertThat(response.enabled()).isTrue();
        assertThat(response.createdAt()).isEqualTo(Instant.ofEpochMilli(1714521600000L));
        assertThat(response.roles()).containsExactly("ADMIN", "USER");
    }

    @Test
    void should_returnFalseEnabled_when_keycloakReportedNull() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, null, false, 0L);

        AdminUserResponse response = mapper.toResponse(source, List.of());

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void should_returnFalseEnabled_when_keycloakReportedFalse() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, false, false, 0L);

        AdminUserResponse response = mapper.toResponse(source, List.of());

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void should_returnNullCreatedAt_when_timestampIsNull() {
        KeycloakUser source = new KeycloakUser("id", "u", "e", null, null, true, false, null);

        AdminUserResponse response = mapper.toResponse(source, List.of());

        assertThat(response.createdAt()).isNull();
    }
}
