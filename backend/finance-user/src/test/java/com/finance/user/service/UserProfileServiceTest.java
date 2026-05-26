package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.ProfileResponse;
import com.finance.user.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final String USER = "kc-user-1";

    @Mock private KeycloakAdminClient client;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(client);
    }

    @Test
    void get_mapsKeycloakBasicsToProfileResponse() {
        when(client.getUser(USER)).thenReturn(Map.of(
                "username", "alice",
                "firstName", "Alice",
                "lastName", "Liddell",
                "email", "alice@example.com"
        ));

        ProfileResponse result = service.get(USER);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.firstName()).isEqualTo("Alice");
        assertThat(result.lastName()).isEqualTo("Liddell");
        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void get_returnsNullFields_whenKeycloakOmitsThem() {
        when(client.getUser(USER)).thenReturn(Map.of("username", "bob"));

        ProfileResponse result = service.get(USER);

        assertThat(result.username()).isEqualTo("bob");
        assertThat(result.firstName()).isNull();
        assertThat(result.lastName()).isNull();
        assertThat(result.email()).isNull();
    }

    @Test
    void update_callsKeycloakAndReturnsRefreshedProfile() {
        when(client.getUser(USER)).thenReturn(Map.of(
                "username", "carol",
                "firstName", "Carol",
                "lastName", "Danvers",
                "email", "carol@example.com"
        ));
        ProfileUpdateRequest request = new ProfileUpdateRequest("carol", "Carol", "Danvers");

        ProfileResponse result = service.update(USER, request);

        verify(client).updateBasics(USER, "carol", "Carol", "Danvers");
        assertThat(result.username()).isEqualTo("carol");
    }
}
