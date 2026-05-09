package com.finance.portfolio.service;
import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.common.dto.response.PagedResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PortfolioViewResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioFacadeViewTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioCrudService crudService;
    @Mock private PortfolioSummaryService summaryService;
    @Mock private PortfolioPerformanceService performanceService;

    private final PortfolioProperties portfolioProperties = new PortfolioProperties();
    private PortfolioFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PortfolioFacade(portfolioRepository, crudService, summaryService, performanceService, portfolioProperties);
    }

    private void mockOwner() {
        lenient().when(portfolioRepository.findByIdAndUserSub(1L, "user-1"))
                .thenReturn(Optional.of(Portfolio.builder().id(1L).userSub("user-1").build()));
    }

    private PortfolioSummaryResponse summary() {
        return new PortfolioSummaryResponse(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    @Test
    void shouldIncludeAllSections_whenAllRequested() {
        mockOwner();
        when(summaryService.getSummary(1L, null)).thenReturn(summary());
        when(summaryService.getPositionsPaged(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(PagedResponse.of(List.<PositionResponse>of(), 0, 10, 0));
        when(summaryService.getAllocation(1L, "assetType", null)).thenReturn(List.<AllocationItem>of());

        PortfolioViewResponse view = facade.getPortfolioView("user-1", 1L,
                Set.of("summary", "positions", "allocation"));

        assertThat(view.summary()).isNotNull();
        assertThat(view.positions()).isNotNull();
        assertThat(view.allocation()).isNotNull();
    }

    @Test
    void shouldSkipUnrequestedSections_whenIncludeListLimited() {
        mockOwner();
        when(summaryService.getSummary(1L, null)).thenReturn(summary());

        PortfolioViewResponse view = facade.getPortfolioView("user-1", 1L, Set.of("summary"));

        assertThat(view.summary()).isNotNull();
        assertThat(view.positions()).isNull();
        assertThat(view.allocation()).isNull();
        verify(summaryService, never())
                .getPositionsPaged(anyLong(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
        verify(summaryService, never()).getAllocation(anyLong(), eq("assetType"), isNull());
    }

    @Test
    void shouldDelegateToPerformance_whenChartTypeIsPerformance() {
        mockOwner();
        when(performanceService.getPerformance(1L, "1M", null)).thenReturn(List.of());

        Object result = facade.getChart("user-1", 1L, "performance", "1M", null, null);

        assertThat(result).isInstanceOf(List.class);
        verify(performanceService).getPerformance(1L, "1M", null);
    }

    @Test
    void shouldDelegateToAssetSeries_whenChartTypeIsAssetSeries() {
        mockOwner();
        when(performanceService.getAssetSeries(1L, "STOCK", "THYAO.IS", "3M")).thenReturn(List.of());

        Object result = facade.getChart("user-1", 1L, "asset-series", "3M", "STOCK", "THYAO.IS");

        assertThat(result).isInstanceOf(List.class);
        verify(performanceService).getAssetSeries(1L, "STOCK", "THYAO.IS", "3M");
    }
}
