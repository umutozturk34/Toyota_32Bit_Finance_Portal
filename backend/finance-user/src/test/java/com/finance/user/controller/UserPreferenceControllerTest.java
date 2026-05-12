package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.user.dto.enums.ThemePreference;
import com.finance.user.service.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceControllerTest {

    private static final String USER = "kc-user-1";

    @Mock private UserPreferenceService service;
    @Mock private Translator translator;

    private UserPreferenceController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserPreferenceController(service, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void getPreferences_returnsApiResponseWrappingService() {
        UserPreferenceResponse data = new UserPreferenceResponse(USER, ThemePreference.DARK, "tr",
                "Europe/Istanbul", "1M", true);
        when(service.getOrDefault(USER)).thenReturn(data);
        when(translator.translate("api.preferences.retrieved")).thenReturn("retrieved");

        ApiResponse<UserPreferenceResponse> response = controller.getPreferences(jwt);

        assertThat(response.getMessage()).isEqualTo("retrieved");
        assertThat(response.getData()).isSameAs(data);
    }

    @Test
    void updatePreferences_delegatesToServiceAndReturnsApiResponse() {
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                ThemePreference.LIGHT, "en", null, null, true);
        UserPreferenceResponse data = new UserPreferenceResponse(USER, ThemePreference.LIGHT, "en",
                "Europe/Istanbul", "1M", true);
        when(service.upsert(USER, request)).thenReturn(data);
        when(translator.translate("api.preferences.updated")).thenReturn("updated");

        ApiResponse<UserPreferenceResponse> response = controller.updatePreferences(jwt, request);

        assertThat(response.getMessage()).isEqualTo("updated");
        assertThat(response.getData()).isSameAs(data);
    }
}
