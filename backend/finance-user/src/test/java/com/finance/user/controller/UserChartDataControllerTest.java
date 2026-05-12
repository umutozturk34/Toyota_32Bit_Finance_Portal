package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartDrawingUpdateRequest;
import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.dto.UserChartPreferenceUpdateRequest;
import com.finance.user.service.UserChartDataFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserChartDataControllerTest {

    private static final String USER = "kc-user-1";
    private static final String CODE = "BTC";
    private static final TrackedAssetType TYPE = TrackedAssetType.CRYPTO;

    @Mock private UserChartDataFacade facade;
    @Mock private Translator translator;

    private UserChartDataController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserChartDataController(facade, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void get_returnsApiResponseWrappingBundle() {
        UserChartBundleResponse bundle = new UserChartBundleResponse(
                new UserChartPreferenceResponse(JsonNodeFactory.instance.objectNode(), Instant.now()),
                new UserChartDrawingResponse(JsonNodeFactory.instance.arrayNode(), Instant.now()));
        when(facade.getBundle(USER, TYPE, CODE, "1D")).thenReturn(bundle);
        when(translator.translate("api.chart.dataRetrieved")).thenReturn("retrieved");

        ApiResponse<UserChartBundleResponse> response = controller.get(jwt, TYPE, CODE, "1D");

        assertThat(response.getMessage()).isEqualTo("retrieved");
        assertThat(response.getData()).isSameAs(bundle);
    }

    @Test
    void updatePreferences_delegatesConfigToFacade() {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("chartType", "candle");
        UserChartPreferenceUpdateRequest request = new UserChartPreferenceUpdateRequest(config);
        UserChartPreferenceResponse data = new UserChartPreferenceResponse(config, Instant.now());
        when(facade.upsertPreferences(USER, TYPE, CODE, config)).thenReturn(data);
        when(translator.translate("api.chart.preferencesUpdated")).thenReturn("updated");

        ApiResponse<UserChartPreferenceResponse> response = controller.updatePreferences(jwt, TYPE, CODE, request);

        assertThat(response.getMessage()).isEqualTo("updated");
        assertThat(response.getData()).isSameAs(data);
    }

    @Test
    void updateDrawings_delegatesDrawingsToFacade() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        UserChartDrawingUpdateRequest request = new UserChartDrawingUpdateRequest(drawings);
        UserChartDrawingResponse data = new UserChartDrawingResponse(drawings, Instant.now());
        when(facade.upsertDrawings(USER, TYPE, CODE, (JsonNode) drawings)).thenReturn(data);
        when(translator.translate("api.chart.drawingsUpdated")).thenReturn("updated");

        ApiResponse<UserChartDrawingResponse> response = controller.updateDrawings(jwt, TYPE, CODE, request);

        assertThat(response.getMessage()).isEqualTo("updated");
        assertThat(response.getData()).isSameAs(data);
    }
}
