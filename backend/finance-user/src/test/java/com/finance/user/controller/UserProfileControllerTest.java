package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.ProfileResponse;
import com.finance.user.dto.ProfileUpdateRequest;
import com.finance.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final String USER = "kc-user-1";

    @Mock private UserProfileService service;
    @Mock private Translator translator;

    private UserProfileController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserProfileController(service, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void get_returnsProfileWithTranslatedMessage() {
        ProfileResponse expected = new ProfileResponse("umut", "Umut", "Ozturk", "umut@x.com");
        when(service.get(USER)).thenReturn(expected);
        when(translator.translate("api.profile.retrieved")).thenReturn("ok");

        ApiResponse<ProfileResponse> response = controller.get(jwt);

        assertThat(response.getMessage()).isEqualTo("ok");
        assertThat(response.getData()).isSameAs(expected);
        verify(service).get(USER);
    }

    @Test
    void update_delegatesToServiceAndTranslatesMessage() {
        ProfileUpdateRequest request = new ProfileUpdateRequest("newname", "New", "Name");
        ProfileResponse expected = new ProfileResponse("newname", "New", "Name", "umut@x.com");
        when(service.update(USER, request)).thenReturn(expected);
        when(translator.translate("api.profile.updated")).thenReturn("updated");

        ApiResponse<ProfileResponse> response = controller.update(jwt, request);

        assertThat(response.getMessage()).isEqualTo("updated");
        assertThat(response.getData()).isSameAs(expected);
        verify(service).update(USER, request);
    }
}
