package com.finance.backend.service;

import com.finance.backend.client.KeycloakAdminClient;
import com.finance.backend.dto.AdminUserResponse;
import com.finance.backend.dto.KeycloakUser;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.KeycloakUserMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private KeycloakAdminClient client;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(client, new KeycloakUserMapperImpl());
    }

    @Test
    void shouldMapKeycloakUsersToResponses_whenListing() {
        KeycloakUser u1 = new KeycloakUser("id-1", "alice", "alice@example.com",
                "Alice", "Wonder", true, 1714560000000L);
        KeycloakUser u2 = new KeycloakUser("id-2", "bob", null, "Bob", null, false, null);
        when(client.listUsers(0, 50, null)).thenReturn(List.of(u1, u2));

        List<AdminUserResponse> result = service.listUsers(0, 50, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(0).enabled()).isTrue();
        assertThat(result.get(0).createdAt()).isNotNull();
        assertThat(result.get(1).enabled()).isFalse();
        assertThat(result.get(1).createdAt()).isNull();
    }

    @Test
    void shouldReturnEmptyList_whenClientReturnsNull() {
        when(client.listUsers(0, 50, null)).thenReturn(null);

        List<AdminUserResponse> result = service.listUsers(0, 50, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDelegateBan_toClientWithFalseEnabled() {
        service.banUser("user-id-123", "admin-sub");

        verify(client).setEnabled("user-id-123", false);
    }

    @Test
    void shouldRejectSelfBan_whenCallerEqualsTarget() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.finance.backend.exception.BusinessException.class,
                () -> service.banUser("admin-sub", "admin-sub"));
        verify(client, org.mockito.Mockito.never()).setEnabled(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldDelegateUnban_toClientWithTrueEnabled() {
        service.unbanUser("user-id-456");

        verify(client).setEnabled("user-id-456", true);
    }

    @Test
    void shouldDelegateCount_toClientReturningTotal() {
        when(client.countUsers("alice")).thenReturn(42L);

        long total = service.countUsers("alice");

        assertThat(total).isEqualTo(42L);
    }

    @Test
    void shouldTreatNullEnabled_asFalseInResponse() {
        KeycloakUser legacy = new KeycloakUser("legacy", "legacyuser", null, null, null, null, null);
        when(client.listUsers(0, 50, null)).thenReturn(List.of(legacy));

        List<AdminUserResponse> result = service.listUsers(0, 50, null);

        assertThat(result.get(0).enabled()).isFalse();
    }

    @Test
    void shouldPropagateResourceNotFound_whenBanningUnknownUser() {
        doThrow(new ResourceNotFoundException("Keycloak resource not found for setEnabled"))
                .when(client).setEnabled("missing", false);

        assertThatThrownBy(() -> service.banUser("missing", "admin-sub"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldPropagateExternalApiException_whenKeycloakUnreachable() {
        doThrow(new ExternalApiException("KEYCLOAK", "network failure"))
                .when(client).setEnabled("user-1", true);

        assertThatThrownBy(() -> service.unbanUser("user-1"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("network failure");
    }
}
