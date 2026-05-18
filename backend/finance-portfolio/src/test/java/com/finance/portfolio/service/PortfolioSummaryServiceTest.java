package com.finance.portfolio.service;


import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
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
    @Mock private DerivativePositionRepository derivativePositionRepository;

    private CountingAssetPricingPort counting;
    private PortfolioResponseMapper responseMapper;
    private PortfolioSummaryService service;

    @BeforeEach
    void setUp() {
        counting = new CountingAssetPricingPort();
        responseMapper = new PortfolioResponseMapperImpl();
        service = new PortfolioSummaryService(counting, positionRepository, responseMapper,
                assetSnapshotRepository, derivativePositionRepository);
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findOpenByPortfolio(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.List.of());
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

    private ViopContract sampleContract(BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol("F_USDTRY0626")
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(new BigDecimal("1000"))
                .initialMargin(new BigDecimal("3500.00"))
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private DerivativePosition longOpenPosition() {
        return DerivativePosition.builder()
                .id(50L)
                .direction(DerivativeDirection.LONG)
                .entryDate(java.time.LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35.20"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(sampleContract(new BigDecimal("35.50")))
                .build();
    }

    @Test
    void shouldAppendDerivativeRowsToPositions_whenPortfolioHasOpenViop() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longOpenPosition()));

        List<PositionResponse> result = service.getPositions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assetType()).isEqualTo("VIOP");
        assertThat(result.get(0).assetCode()).isEqualTo("F_USDTRY0626");
        assertThat(result.get(0).derivative()).isNotNull();
        assertThat(result.get(0).derivative().direction()).isEqualTo("LONG");
        assertThat(result.get(0).derivative().lockedMarginTry()).isEqualByComparingTo("3500.00");
    }

    @Test
    void shouldAppendKapaliSuffixToClosedDerivativeName_whenPositionIsClosed() {
        DerivativePosition closed = longOpenPosition();
        closed.closeWith(java.time.LocalDate.of(2026, 5, 1),
                new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(closed));

        List<PositionResponse> result = service.getPositions(1L);

        assertThat(result.get(0).assetName()).contains("KAPALI");
        assertThat(result.get(0).derivative().closed()).isTrue();
    }

    @Test
    void shouldIncludeDerivativesInAllocation_whenGroupingByAssetType() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longOpenPosition()));

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", null);

        assertThat(allocation).extracting(AllocationItem::label).contains("VIOP");
        AllocationItem viop = allocation.stream().filter(a -> a.label().equals("VIOP")).findFirst().orElseThrow();
        assertThat(viop.valueTry()).isPositive();
    }

    @Test
    void shouldExcludeDerivatives_whenAllocationFilteredToSpotAssetType() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(longOpenPosition()));

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", "STOCK");

        assertThat(allocation).isEmpty();
    }

    @Test
    void shouldIncludeDerivativeNotionalAndPnl_whenSummaryAggregatesAllTypes() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(longOpenPosition()));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.totalValueTry()).isPositive();
        assertThat(summary.totalEntryValueTry()).isPositive();
    }

    @Test
    void shouldFilterSummaryToViopOnly_whenAssetTypeIsViop() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(longOpenPosition()));

        PortfolioSummaryResponse summary = service.getSummary(1L, "VIOP");

        assertThat(summary.totalValueTry()).isPositive();
    }

    @Test
    void shouldComputePagedPositions_whenGetPositionsPagedInvokedWithSearchAndSort() {
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));
        counting.seedPrice("STOCK", "AKBNK.IS", new BigDecimal("55"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100"), new BigDecimal("40")),
                        stubPosition(AssetType.STOCK, "AKBNK.IS", new BigDecimal("50"), new BigDecimal("45"))));

        com.finance.common.dto.response.PagedResponse<PositionResponse> result =
                service.getPositionsPaged(1L, "THY", null, "profitAmount", "desc", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).assetCode()).isEqualTo("THYAO.IS");
    }

    @Test
    void shouldReturnEmptyPage_whenSearchMatchesNoPositions() {
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.STOCK, "THYAO.IS",
                        new BigDecimal("100"), new BigDecimal("40"))));

        com.finance.common.dto.response.PagedResponse<PositionResponse> result =
                service.getPositionsPaged(1L, "ZZZZ", null, "assetCode", "asc", 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void shouldComputeWeightedAverageAndTotals_whenAggregatingMultipleOpenLots() {
        counting.seedPrice("STOCK", "GARAN.IS", new BigDecimal("100"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.STOCK, "GARAN.IS", new BigDecimal("10"), new BigDecimal("80")),
                        stubPosition(AssetType.STOCK, "GARAN.IS", new BigDecimal("30"), new BigDecimal("90")),
                        stubPosition(AssetType.STOCK, "AKBNK.IS", new BigDecimal("5"), new BigDecimal("60"))));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "STOCK", "GARAN.IS");

        assertThat(result.lotCount()).isEqualTo(2);
        assertThat(result.totalQuantity()).isEqualByComparingTo("40");
        assertThat(result.weightedAvgEntryPrice()).isEqualByComparingTo("87.5000");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("4000.0000");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("500.0000");
    }

    @Test
    void shouldExcludeClosedLots_whenAggregatingAsset() {
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("60"));
        PortfolioPosition closed = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("20"), new BigDecimal("50"));
        closed.closeWith(LocalDateTime.now(), new BigDecimal("55"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("10"), new BigDecimal("45")),
                        closed));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "STOCK", "THYAO.IS");

        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalQuantity()).isEqualByComparingTo("10");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("600.0000");
    }

    @Test
    void shouldReturnZeroAggregate_whenNoOpenLotsMatchAsset() {
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.STOCK, "AKBNK.IS",
                        new BigDecimal("5"), new BigDecimal("40"))));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "STOCK", "THYAO.IS");

        assertThat(result.lotCount()).isZero();
        assertThat(result.totalQuantity()).isEqualByComparingTo("0");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("0");
    }
}
