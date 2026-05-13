package com.finance.portfolio.controller;

import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.LotLimitsResponse;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PortfolioViewResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.service.PortfolioBackfillTracker;
import com.finance.portfolio.service.PortfolioFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    private static final String USER = "kc-user-1";

    @Mock private AppProperties appProperties;
    @Mock private PortfolioFacade portfolioFacade;
    @Mock private PortfolioBackfillTracker backfillTracker;
    @Mock private Translator translator;

    private PortfolioController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new PortfolioController(appProperties, portfolioFacade, backfillTracker, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void listPortfolios_delegatesToFacade() {
        List<PortfolioResponse> data = List.of();
        when(portfolioFacade.listPortfolios(USER)).thenReturn(data);
        when(translator.translate("api.portfolio.listRetrieved")).thenReturn("ok");

        ApiResponse<List<PortfolioResponse>> response = controller.listPortfolios(jwt);

        assertThat(response.getMessage()).isEqualTo("ok");
        assertThat(response.getData()).isSameAs(data);
    }

    @Test
    void getLotLimits_returnsLimitsFromFacade() {
        LotLimitsResponse limits = mock(LotLimitsResponse.class);
        when(portfolioFacade.getLotLimits()).thenReturn(limits);
        when(translator.translate("api.portfolio.lotLimitsRetrieved")).thenReturn("ok");

        ApiResponse<LotLimitsResponse> response = controller.getLotLimits();

        assertThat(response.getData()).isSameAs(limits);
    }

    @Test
    void streamBackfillStatus_checksOwnershipBeforeSubscribing() {
        SseEmitter emitter = new SseEmitter();
        when(backfillTracker.subscribe(42L)).thenReturn(emitter);

        SseEmitter result = controller.streamBackfillStatus(jwt, 42L);

        verify(portfolioFacade).requireOwnership(USER, 42L);
        assertThat(result).isSameAs(emitter);
    }

    @Test
    void createPortfolio_delegatesToFacade() {
        PortfolioCreateRequest request = new PortfolioCreateRequest("My");
        PortfolioResponse expected = portfolio();
        when(portfolioFacade.createPortfolio(USER, request)).thenReturn(expected);
        when(translator.translate("api.portfolio.created")).thenReturn("created");

        ApiResponse<PortfolioResponse> response = controller.createPortfolio(jwt, request);

        assertThat(response.getMessage()).isEqualTo("created");
        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void addPosition_delegatesToFacade() {
        PositionRequest request = positionRequest();
        PositionResponse expected = position(1L);
        when(portfolioFacade.addPosition(USER, 7L, request)).thenReturn(expected);
        when(translator.translate("api.portfolio.positionCreated")).thenReturn("created");

        ApiResponse<PositionResponse> response = controller.addPosition(jwt, 7L, request);

        assertThat(response.getMessage()).isEqualTo("created");
        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void updatePosition_delegatesToFacade() {
        PositionRequest request = positionRequest();
        PositionResponse expected = position(1L);
        when(portfolioFacade.updatePosition(USER, 7L, 1L, request)).thenReturn(expected);
        when(translator.translate("api.portfolio.positionUpdated")).thenReturn("updated");

        ApiResponse<PositionResponse> response = controller.updatePosition(jwt, 7L, 1L, request);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void deletePosition_returnsApiResponseAndDelegatesToFacade() {
        when(translator.translate("api.portfolio.positionDeleted")).thenReturn("deleted");

        ApiResponse<Void> response = controller.deletePosition(jwt, 7L, 1L);

        assertThat(response.getMessage()).isEqualTo("deleted");
        verify(portfolioFacade).deletePosition(USER, 7L, 1L);
    }

    @Test
    void getPositions_appliesDefaultSize_whenRequestedSizeMissing() {
        AppProperties.Pagination pagination = pagination();
        when(appProperties.getPagination()).thenReturn(pagination);
        PagedResponse<PositionResponse> data = PagedResponse.of(List.of(), 0, 10, 0);
        when(portfolioFacade.getPositionsPaged(eq(USER), eq(7L), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(data);

        controller.getPositions(jwt, 7L, null, null, null, null, 0, null);

        verify(portfolioFacade).getPositionsPaged(USER, 7L, null, null, null, null, 0, 10);
    }

    @ParameterizedTest
    @CsvSource({
            "5, 5",
            "10, 10",
            "200, 100",
            "0, 1",
            "-5, 1"
    })
    void getPositions_clampsRequestedSizeToBounds(int requested, int expected) {
        AppProperties.Pagination pagination = pagination();
        when(appProperties.getPagination()).thenReturn(pagination);
        PagedResponse<PositionResponse> data = PagedResponse.of(List.of(), 0, expected, 0);
        when(portfolioFacade.getPositionsPaged(eq(USER), eq(7L), any(), any(), any(), any(), anyInt(), eq(expected)))
                .thenReturn(data);

        controller.getPositions(jwt, 7L, null, null, null, null, 0, requested);

        verify(portfolioFacade).getPositionsPaged(USER, 7L, null, null, null, null, 0, expected);
    }

    @Test
    void getSummary_delegatesToFacade() {
        PortfolioSummaryResponse summary = mock(PortfolioSummaryResponse.class);
        when(portfolioFacade.getSummary(USER, 7L, null)).thenReturn(summary);
        when(translator.translate("api.portfolio.summaryRetrieved")).thenReturn("ok");

        ApiResponse<PortfolioSummaryResponse> response = controller.getSummary(jwt, 7L, null);

        assertThat(response.getData()).isSameAs(summary);
    }

    @Test
    void getAllocation_delegatesToFacade() {
        List<AllocationItem> allocation = List.of();
        when(portfolioFacade.getAllocation(USER, 7L, "assetType", null)).thenReturn(allocation);
        when(translator.translate("api.portfolio.allocationRetrieved")).thenReturn("ok");

        ApiResponse<List<AllocationItem>> response = controller.getAllocation(jwt, 7L, "assetType", null);

        assertThat(response.getData()).isSameAs(allocation);
    }

    @Test
    void getPortfolioView_splitsIncludeParamIntoSet() {
        PortfolioViewResponse view = mock(PortfolioViewResponse.class);
        when(portfolioFacade.getPortfolioView(eq(USER), eq(7L), any())).thenReturn(view);
        when(translator.translate("api.portfolio.viewRetrieved")).thenReturn("ok");

        ApiResponse<PortfolioViewResponse> response = controller.getPortfolioView(jwt, 7L, "summary,positions");

        assertThat(response.getData()).isSameAs(view);
        verify(portfolioFacade).getPortfolioView(USER, 7L, Set.of("summary", "positions"));
    }

    @Test
    void getChart_delegatesAllArgsToFacade() {
        Object chart = new Object();
        when(portfolioFacade.getChart(USER, 7L, "performance", "1M", null, null)).thenReturn(chart);
        when(translator.translate("api.portfolio.chartRetrieved")).thenReturn("ok");

        ApiResponse<?> response = controller.getChart(jwt, 7L, "performance", "1M", null, null);

        assertThat(response.getData()).isSameAs(chart);
    }

    private AppProperties.Pagination pagination() {
        AppProperties.Pagination p = new AppProperties.Pagination();
        AppProperties.PortfolioPage portfolioPage = new AppProperties.PortfolioPage();
        portfolioPage.setPositionsDefaultSize(10);
        portfolioPage.setMaxSize(100);
        p.setPortfolio(portfolioPage);
        return p;
    }

    private PortfolioResponse portfolio() {
        return org.mockito.Mockito.mock(PortfolioResponse.class);
    }

    private PositionResponse position(Long id) {
        return org.mockito.Mockito.mock(PositionResponse.class);
    }

    private PositionRequest positionRequest() {
        return org.mockito.Mockito.mock(PositionRequest.class);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
