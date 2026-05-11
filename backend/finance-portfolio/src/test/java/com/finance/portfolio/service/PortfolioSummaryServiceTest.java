package com.finance.portfolio.service;


import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.mapper.PortfolioResponseMapperImpl;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSummaryServiceTest {

    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    private CountingAssetPricingPort counting;
    private PortfolioResponseMapper responseMapper;
    private PortfolioSummaryService service;

    @BeforeEach
    void setUp() {
        counting = new CountingAssetPricingPort();
        responseMapper = new PortfolioResponseMapperImpl();
        service = new PortfolioSummaryService(counting, positionRepository, responseMapper,
                assetSnapshotRepository);
    }

    @Test
    void shouldIssueExactlyOneBundlesBatchCall_whenFetchingPositions() {
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("50"));
        counting.seedPrice("FUND", "AAK", new BigDecimal("110"));

        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1"), new BigDecimal("2400000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40")),
                        stubPosition(AssetType.FUND, "AAK", new BigDecimal("50"), new BigDecimal("100"))));

        List<PositionResponse> result = service.getPositions(1L);

        assertThat(result).hasSize(3);
        assertThat(counting.batchBundlesCalls()).isEqualTo(1);
        assertThat(counting.priceCalls()).isEqualTo(0);
    }

    @Test
    void shouldAggregateValueAndPnlAcrossPositions_whenComputingSummary() {
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));

        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1"), new BigDecimal("2400000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40"))));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.totalValueTry()).isEqualByComparingTo(new BigDecimal("2506000.0000"));
        assertThat(summary.totalEntryValueTry()).isEqualByComparingTo(new BigDecimal("2404000.0000"));
        assertThat(summary.totalPnlTry()).isEqualByComparingTo(new BigDecimal("102000.0000"));
    }

    @Test
    void shouldRestrictSummaryToOneType_whenAssetTypeFilterProvided() {
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000"));

        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40")),
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1"), new BigDecimal("2400000"))));

        PortfolioSummaryResponse stockOnly = service.getSummary(1L, "STOCK");

        assertThat(stockOnly.totalValueTry()).isEqualByComparingTo(new BigDecimal("6000.0000"));
        assertThat(stockOnly.totalEntryValueTry()).isEqualByComparingTo(new BigDecimal("4000.0000"));
    }

    @Test
    void shouldGroupValuesAndComputePercentages_whenAllocatingByAssetType() {
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("100"));

        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40")),
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("40"), new BigDecimal("80"))));

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", null);

        assertThat(allocation).hasSize(2);
        BigDecimal totalValue = allocation.stream()
                .map(AllocationItem::valueTry)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalValue).isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(allocation.get(0).label()).isEqualTo("STOCK");
        assertThat(allocation.get(0).percent()).isEqualByComparingTo(new BigDecimal("60.0000"));
    }

    private PortfolioPosition stubPosition(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }
}
