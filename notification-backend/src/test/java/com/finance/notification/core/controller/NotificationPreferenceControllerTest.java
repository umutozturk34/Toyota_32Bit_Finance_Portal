package com.finance.notification.core.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.core.service.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    private static final String SUB = "user-1";

    @Mock private NotificationPreferenceService service;
    @Mock private Translator translator;

    private NotificationPreferenceController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new NotificationPreferenceController(service, translator);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .claim("sub", SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NotificationPreferenceResponse response() {
        return new NotificationPreferenceResponse(true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true, true, true, "BIST");
    }

    @Test
    void should_returnPreferences_when_getInvoked() {
        when(service.getOrDefault(SUB)).thenReturn(response());

        ApiResponse<NotificationPreferenceResponse> result = controller.get(jwt);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().marketSessionMarkets()).isEqualTo("BIST");
        verify(service).getOrDefault(SUB);
    }

    @Test
    void should_upsertPreferences_when_patchInvoked() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
                Boolean.FALSE, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        when(service.upsert(SUB, request)).thenReturn(response());

        ApiResponse<NotificationPreferenceResponse> result = controller.upsert(jwt, request);

        assertThat(result.isSuccess()).isTrue();
        verify(service).upsert(SUB, request);
    }
}
