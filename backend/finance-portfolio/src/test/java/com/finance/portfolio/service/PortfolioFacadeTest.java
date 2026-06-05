package com.finance.portfolio.service;

import com.finance.portfolio.service.performance.PortfolioPerformanceService;
import com.finance.portfolio.service.summary.PortfolioSummaryService;

import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.LotLimitsResponse;
import com.finance.portfolio.dto.response.PortfolioResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioFacadeTest {

    private static final String USER = "kc-user-1";
    private static final Long PORTFOLIO_ID = 7L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioCrudService crudService;
    @Mock private PortfolioSummaryService summaryService;
    @Mock private PortfolioPerformanceService performanceService;
    @Mock private PortfolioProperties portfolioProperties;

    private PortfolioFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PortfolioFacade(portfolioRepository, crudService, summaryService,
                performanceService, portfolioProperties);
    }

    @Test
    void getLotLimits_returnsPropertyValuesAndTodayAsMaxEntryDate() {
        LotLimits limits = new LotLimits();
        limits.setMinEntryDate(LocalDate.of(2000, 1, 1));
        limits.setMinPriceTry(new BigDecimal("0.01"));
        limits.setMaxPriceTry(new BigDecimal("1000000"));
        limits.setMinQuantity(new BigDecimal("0.0001"));
        limits.setMaxQuantity(new BigDecimal("1000000000"));
        when(portfolioProperties.getLotLimits()).thenReturn(limits);

        LotLimitsResponse response = facade.getLotLimits();

        assertThat(response.minEntryDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(response.maxEntryDate()).isEqualTo(LocalDate.now());
        assertThat(response.minPriceTry()).isEqualByComparingTo("0.01");
        assertThat(response.maxQuantity()).isEqualByComparingTo("1000000000");
    }

    @Test
    void listPortfolios_delegatesToCrudService() {
        List<PortfolioResponse> data = List.of();
        when(crudService.listPortfolios(USER)).thenReturn(data);

        assertThat(facade.listPortfolios(USER)).isSameAs(data);
    }

    @Test
    void createPortfolio_delegatesToCrudService() {
        PortfolioCreateRequest request = new PortfolioCreateRequest("My");
        PortfolioResponse data = mock(PortfolioResponse.class);
        when(crudService.createPortfolio(USER, request)).thenReturn(data);

        assertThat(facade.createPortfolio(USER, request)).isSameAs(data);
    }

    @Test
    void addPosition_delegatesToCrudService() {
        PositionRequest request = mock(PositionRequest.class);
        PositionResponse data = mock(PositionResponse.class);
        when(crudService.addPosition(PORTFOLIO_ID, USER, request)).thenReturn(data);

        assertThat(facade.addPosition(USER, PORTFOLIO_ID, request)).isSameAs(data);
    }

    @Test
    void updatePosition_delegatesToCrudService() {
        PositionRequest request = mock(PositionRequest.class);
        PositionResponse data = mock(PositionResponse.class);
        when(crudService.updatePosition(PORTFOLIO_ID, 5L, USER, request)).thenReturn(data);

        assertThat(facade.updatePosition(USER, PORTFOLIO_ID, 5L, request)).isSameAs(data);
    }

    @Test
    void deletePosition_delegatesToCrudService() {
        facade.deletePosition(USER, PORTFOLIO_ID, 5L);

        verify(crudService).deletePosition(PORTFOLIO_ID, 5L, USER);
    }

    @Test
    void requireOwnership_throwsResourceNotFound_whenPortfolioNotOwnedByUser() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.requireOwnership(USER, PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    @Test
    void requireOwnership_passes_whenPortfolioOwnedByUser() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));

        facade.requireOwnership(USER, PORTFOLIO_ID);
    }

    @Test
    void getSummary_validatesOwnerThenDelegates() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        PortfolioSummaryResponse summary = mock(PortfolioSummaryResponse.class);
        when(summaryService.getSummary(PORTFOLIO_ID, null)).thenReturn(summary);

        assertThat(facade.getSummary(USER, PORTFOLIO_ID, null)).isSameAs(summary);
    }

    @Test
    void getSummary_throwsAndSkipsSummaryService_whenUserDoesNotOwnPortfolio() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getSummary(USER, PORTFOLIO_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(summaryService, never()).getSummary(any(), any());
    }

    @Test
    void getPositionsPaged_validatesOwnerThenDelegates() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        PagedResponse<PositionResponse> page = PagedResponse.of(List.of(), 0, 10, 0);
        when(summaryService.getPositionsPaged(PORTFOLIO_ID, "ak", "STOCK", "price", "asc", null, 0, 10))
                .thenReturn(page);

        assertThat(facade.getPositionsPaged(USER, PORTFOLIO_ID, "ak", "STOCK", "price", "asc", null, 0, 10))
                .isSameAs(page);
    }

    @Test
    void getAllocation_validatesOwnerThenDelegates() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        List<AllocationItem> allocation = List.of();
        when(summaryService.getAllocation(PORTFOLIO_ID, "assetType", null, null)).thenReturn(allocation);

        assertThat(facade.getAllocation(USER, PORTFOLIO_ID, "assetType", null, null)).isSameAs(allocation);
    }

    @Test
    void getPortfolioView_includesOnlyRequestedSections() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        PortfolioSummaryResponse summary = mock(PortfolioSummaryResponse.class);
        when(summaryService.getSummary(PORTFOLIO_ID, null)).thenReturn(summary);

        PortfolioViewResponse response = facade.getPortfolioView(USER, PORTFOLIO_ID, Set.of("summary"));

        assertThat(response.summary()).isSameAs(summary);
        assertThat(response.positions()).isNull();
        assertThat(response.allocation()).isNull();
    }

    @Test
    void getPortfolioView_assemblesAllSections_whenAllIncludesPresent() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        when(portfolioProperties.getView()).thenReturn(new PortfolioProperties.View());
        when(summaryService.getSummary(PORTFOLIO_ID, null)).thenReturn(mock(PortfolioSummaryResponse.class));
        when(summaryService.getPositionsPaged(PORTFOLIO_ID, null, null, null, null, null, 0, 10))
                .thenReturn(PagedResponse.of(List.of(), 0, 10, 0));
        when(summaryService.getAllocation(PORTFOLIO_ID, "assetType", null, null)).thenReturn(List.of());

        PortfolioViewResponse response = facade.getPortfolioView(USER, PORTFOLIO_ID,
                Set.of("summary", "positions", "allocation"));

        assertThat(response.summary()).isNotNull();
        assertThat(response.positions()).isNotNull();
        assertThat(response.allocation()).isNotNull();
    }

    @Test
    void getChart_returnsAssetSeries_whenTypeIsAssetSeries() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        List<com.finance.portfolio.dto.response.AssetSeriesPoint> data = List.of();
        when(performanceService.getAssetSeries(PORTFOLIO_ID, "STOCK", "AKBNK", "1M", null)).thenReturn(data);

        Object result = facade.getChart(USER, PORTFOLIO_ID, "asset-series", "1M", "STOCK", "AKBNK", null);

        assertThat(result).isSameAs(data);
        verify(performanceService, never()).getPerformance(any(), any(), any());
    }

    @Test
    void getChart_returnsPerformance_whenTypeIsNotAssetSeries() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        List<com.finance.portfolio.dto.response.PerformancePoint> data = List.of();
        when(performanceService.getPerformance(PORTFOLIO_ID, "1M", "STOCK")).thenReturn(data);

        Object result = facade.getChart(USER, PORTFOLIO_ID, "performance", "1M", "STOCK", null, null);

        assertThat(result).isSameAs(data);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
