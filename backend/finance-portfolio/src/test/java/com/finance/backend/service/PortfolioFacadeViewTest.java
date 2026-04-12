package com.finance.backend.service;

import com.finance.backend.dto.response.*;
import com.finance.backend.model.Portfolio;
import com.finance.backend.repository.PortfolioRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioFacadeViewTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private OnboardingService onboardingService;
    @Mock private PortfolioCrudService crudService;
    @Mock private PortfolioTransactionService transactionService;
    @Mock private PortfolioSummaryService summaryService;
    @Mock private PortfolioPerformanceService performanceService;

    private PortfolioFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PortfolioFacade(portfolioRepository, onboardingService, crudService,
                transactionService, summaryService, performanceService);
    }

    private void mockOwner() {
        lenient().when(portfolioRepository.findByIdAndUserSub(1L, "user-1"))
                .thenReturn(Optional.of(Portfolio.builder().id(1L).userSub("user-1").build()));
    }

    @Test
    void getPortfolioViewIncludesAllSections() {
        mockOwner();
        PortfolioSummaryResponse summary = new PortfolioSummaryResponse(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(summaryService.getSummary(1L, null)).thenReturn(summary);
        when(summaryService.getPositionsPaged(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(PagedResponse.of(List.of(), 0, 10, 0));
        when(crudService.listTransactionsPaged(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(0), eq(5)))
                .thenReturn(PagedResponse.of(List.of(), 0, 5, 0));
        when(summaryService.getAllocation(1L, "assetType", null)).thenReturn(List.of());

        PortfolioViewResponse view = facade.getPortfolioView("user-1", 1L,
                Set.of("summary", "positions", "transactions", "allocation"));

        assertThat(view.summary()).isNotNull();
        assertThat(view.positions()).isNotNull();
        assertThat(view.recentTransactions()).isNotNull();
        assertThat(view.allocation()).isNotNull();
    }

    @Test
    void getPortfolioViewSkipsUnrequestedSections() {
        mockOwner();
        PortfolioSummaryResponse summary = new PortfolioSummaryResponse(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(summaryService.getSummary(1L, null)).thenReturn(summary);

        PortfolioViewResponse view = facade.getPortfolioView("user-1", 1L, Set.of("summary"));

        assertThat(view.summary()).isNotNull();
        assertThat(view.positions()).isNull();
        assertThat(view.recentTransactions()).isNull();
        assertThat(view.allocation()).isNull();
        verify(crudService, never()).listTransactionsPaged(anyLong(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void getChartDelegatesToPerformanceForPerformanceType() {
        mockOwner();
        when(performanceService.getPerformance(1L, "1M", null)).thenReturn(List.of());

        Object result = facade.getChart("user-1", 1L, "performance", "1M", null, null);

        assertThat(result).isInstanceOf(List.class);
        verify(performanceService).getPerformance(1L, "1M", null);
    }

    @Test
    void getChartDelegatesToAssetSeriesForAssetSeriesType() {
        mockOwner();
        when(performanceService.getAssetSeries(1L, "STOCK", "THYAO.IS", "3M")).thenReturn(List.of());

        Object result = facade.getChart("user-1", 1L, "asset-series", "3M", "STOCK", "THYAO.IS");

        assertThat(result).isInstanceOf(List.class);
        verify(performanceService).getAssetSeries(1L, "STOCK", "THYAO.IS", "3M");
    }
}
