package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.KeycloakUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleRegistrationCleanupServiceTest {

    private static final String TTL = "48h";
    private static final long NOW = System.currentTimeMillis();
    private static final long STALE = NOW - Duration.ofHours(100).toMillis();
    private static final long RECENT = NOW - Duration.ofHours(1).toMillis();

    @Mock
    private KeycloakAdminClient adminClient;

    private StaleRegistrationCleanupService service;

    @BeforeEach
    void setUp() {
        service = new StaleRegistrationCleanupService(adminClient, true, TTL, 200);
    }

    private KeycloakUser user(String id, Boolean emailVerified, Long createdTimestamp) {
        return new KeycloakUser(id, id, id + "@x.com", null, null, true, emailVerified, createdTimestamp);
    }

    @Test
    void should_deleteUser_when_unverifiedAndOlderThanTtl() {
        when(adminClient.listUnverifiedUsers(0, 200)).thenReturn(List.of(user("stale", false, STALE)));

        service.purgeStaleUnverifiedRegistrations();

        verify(adminClient).deleteUser("stale");
    }

    @Test
    void should_notDelete_when_unverifiedButWithinTtl() {
        when(adminClient.listUnverifiedUsers(0, 200)).thenReturn(List.of(user("recent", false, RECENT)));

        service.purgeStaleUnverifiedRegistrations();

        verify(adminClient, never()).deleteUser("recent");
    }

    @Test
    void should_notDelete_when_emailReportedVerified() {
        when(adminClient.listUnverifiedUsers(0, 200)).thenReturn(List.of(user("verified", true, STALE)));

        service.purgeStaleUnverifiedRegistrations();

        verify(adminClient, never()).deleteUser("verified");
    }

    @Test
    void should_notDelete_when_createdTimestampMissing() {
        when(adminClient.listUnverifiedUsers(0, 200)).thenReturn(List.of(user("noTimestamp", false, null)));

        service.purgeStaleUnverifiedRegistrations();

        verify(adminClient, never()).deleteUser("noTimestamp");
    }

    @Test
    void should_notQueryKeycloak_when_disabled() {
        StaleRegistrationCleanupService disabled = new StaleRegistrationCleanupService(adminClient, false, TTL, 200);

        disabled.purgeStaleUnverifiedRegistrations();

        verify(adminClient, never()).listUnverifiedUsers(anyInt(), anyInt());
    }

    @Test
    void should_continueDeleting_when_oneDeletionFails() {
        when(adminClient.listUnverifiedUsers(0, 200))
                .thenReturn(List.of(user("boom", false, STALE), user("ok", false, STALE)));
        doThrow(new RuntimeException("kc down")).when(adminClient).deleteUser("boom");

        service.purgeStaleUnverifiedRegistrations();

        verify(adminClient).deleteUser("ok");
    }
}
