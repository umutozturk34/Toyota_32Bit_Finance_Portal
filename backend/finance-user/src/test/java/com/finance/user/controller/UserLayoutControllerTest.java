package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.service.UserLayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLayoutControllerTest {

    private static final String USER = "kc-user-1";

    @Mock private UserLayoutService service;
    @Mock private Translator translator;

    private UserLayoutController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserLayoutController(service, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void getLayout_returnsApiResponseWrappingService() {
        UserLayoutResponse data = new UserLayoutResponse(USER, JsonNodeFactory.instance.objectNode(), Instant.now());
        when(service.getOrEmpty(USER)).thenReturn(data);
        when(translator.translate("api.layout.retrieved")).thenReturn("retrieved");

        ApiResponse<UserLayoutResponse> response = controller.getLayout(jwt);

        assertThat(response.getMessage()).isEqualTo("retrieved");
        assertThat(response.getData()).isSameAs(data);
    }

    @Test
    void updateOverview_delegatesToServiceAndReturnsApiResponse() {
        JsonNode overview = JsonNodeFactory.instance.objectNode().putArray("sections").objectNode();
        UserLayoutResponse data = new UserLayoutResponse(USER, overview, Instant.now());
        when(service.saveOverview(USER, overview)).thenReturn(data);
        when(translator.translate("api.layout.overviewUpdated")).thenReturn("updated");

        ApiResponse<UserLayoutResponse> response = controller.updateOverview(jwt, overview);

        assertThat(response.getMessage()).isEqualTo("updated");
        assertThat(response.getData()).isSameAs(data);
    }
}
