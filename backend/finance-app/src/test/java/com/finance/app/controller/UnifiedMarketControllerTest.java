package com.finance.app.controller;

import com.finance.app.service.UnifiedMarketService;
import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.dto.response.MarketAvailabilityResponse;
import com.finance.shared.dto.response.GroupCount;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedMarketControllerTest {

    @Mock private AppProperties appProperties;
    @Mock private UnifiedMarketService unifiedMarketService;
    @Mock private Translator translator;

    private UnifiedMarketController controller;

    @BeforeEach
    void setUp() {
        controller = new UnifiedMarketController(appProperties, unifiedMarketService, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getMarketAssets_delegatesToService_withClampedPagination() {
        AppProperties.Market market = new AppProperties.Market();
        AppProperties.Pagination pagination = new AppProperties.Pagination();
        pagination.setMarket(market);
        when(appProperties.getPagination()).thenReturn(pagination);
        PagedResponse<MarketAssetResponse> paged =
                PagedResponse.of(List.of(), 0, 20, 0);
        when(unifiedMarketService.search(List.of(MarketType.STOCK), null, null, null, null,
                null, "desc", "all", 0, 20, null, null)).thenReturn(paged);

        ApiResponse<PagedResponse<MarketAssetResponse>> response = controller.getMarketAssets(
                "STOCK", null, null, null, null, null, "desc", "all", 0, null, null, null);

        assertThat(response).isNotNull();
        verify(unifiedMarketService).search(List.of(MarketType.STOCK), null, null, null, null,
                null, "desc", "all", 0, 20, null, null);
    }

    @Test
    void getMarketAssets_clampsSize_aboveMax() {
        AppProperties.Market market = new AppProperties.Market();
        AppProperties.Pagination pagination = new AppProperties.Pagination();
        pagination.setMarket(market);
        when(appProperties.getPagination()).thenReturn(pagination);
        when(unifiedMarketService.search(
                java.util.Arrays.asList(MarketType.values()), null, null, null, null,
                null, "desc", "all", 0, 100, null, null))
                .thenReturn(PagedResponse.of(List.of(), 0, 100, 0));

        controller.getMarketAssets(null, null, null, null, null, null, "desc", "all", 0, 999, null, null);

        verify(unifiedMarketService).search(
                java.util.Arrays.asList(MarketType.values()), null, null, null, null,
                null, "desc", "all", 0, 100, null, null);
    }

    @Test
    void getMarketHistory_delegatesToService_andUnwrapsApiResponse() {
        when(unifiedMarketService.getHistory(MarketType.STOCK, "THYAO.IS", CandlePeriod.ALL))
                .thenReturn(List.of());

        ApiResponse<List<?>> response = controller.getMarketHistory(
                MarketType.STOCK, "THYAO.IS", CandlePeriod.ALL);

        assertThat(response.isSuccess()).isTrue();
        verify(unifiedMarketService).getHistory(MarketType.STOCK, "THYAO.IS", CandlePeriod.ALL);
    }

    @Test
    void getGroupCounts_delegatesToService() {
        when(unifiedMarketService.getGroupCounts(MarketType.CRYPTO)).thenReturn(List.of());

        ApiResponse<List<GroupCount>> response = controller.getGroupCounts(MarketType.CRYPTO);

        assertThat(response.isSuccess()).isTrue();
        verify(unifiedMarketService).getGroupCounts(MarketType.CRYPTO);
    }

    @Test
    void getMonthlyAvailability_delegatesToService_withYearMonth() {
        MarketAvailabilityResponse availability = new MarketAvailabilityResponse(java.util.Map.of());
        when(unifiedMarketService.getMonthlyAvailability(
                MarketType.STOCK, "THYAO.IS", "2026-04")).thenReturn(availability);

        ApiResponse<MarketAvailabilityResponse> response = controller.getMonthlyAvailability(
                MarketType.STOCK, "THYAO.IS", "2026-04");

        assertThat(response.getData()).isEqualTo(availability);
    }
}
