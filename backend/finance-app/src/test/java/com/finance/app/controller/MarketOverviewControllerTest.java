package com.finance.app.controller;

import com.finance.app.dto.response.overview.AssetCardsData;
import com.finance.app.dto.response.overview.RenderedWidget;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.service.overview.MarketOverviewService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOverviewControllerTest {

    @Mock private MarketOverviewService marketOverviewService;
    @Mock(answer = org.mockito.Answers.RETURNS_DEFAULTS) private com.finance.app.service.overview.WidgetDefinitionService widgetDefinitionService;
    @Mock private Translator translator;
    @InjectMocks private MarketOverviewController controller;

    private Jwt jwt;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void should_returnOverviewWrappedInApiResponse_when_userAuthenticated() {
        RenderedWidget cards = RenderedWidget.of("asset-cards", 0, new AssetCardsData(List.of()));
        when(marketOverviewService.render("user-1")).thenReturn(List.of(cards));

        ApiResponse<List<RenderedWidget>> response = controller.getOverview(jwt);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).sectionId()).isEqualTo("asset-cards");
        assertThat(response.getData().get(0).kind()).isEqualTo(WidgetKind.ASSET_CARDS);
    }

    @Test
    void should_returnEmptyList_when_serviceProducesNoSections() {
        when(marketOverviewService.render("user-1")).thenReturn(List.of());

        ApiResponse<List<RenderedWidget>> response = controller.getOverview(jwt);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void should_passSubjectClaimToService_when_jwtCarriesUserSub() {
        Jwt scopedJwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claim("sub", "kc-uuid-42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(marketOverviewService.render("kc-uuid-42")).thenReturn(List.of());

        ApiResponse<List<RenderedWidget>> response = controller.getOverview(scopedJwt);

        assertThat(response.isSuccess()).isTrue();
    }
}
