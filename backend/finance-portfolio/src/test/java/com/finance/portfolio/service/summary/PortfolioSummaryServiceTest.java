package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.pricing.DerivativePositionFormatter;
import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;

import com.finance.portfolio.service.performance.PortfolioPerformanceService;
import com.finance.portfolio.service.pricing.RealReturnCalculator;


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
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @Mock private PortfolioDailySnapshotRepository portfolioSnapshotRepository;
    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private com.finance.market.viop.repository.ViopCandleRepository viopCandleRepository;
    @Mock private RealReturnCalculator realReturnCalculator;
    @Mock private PortfolioPerformanceService performanceService;

    private CountingAssetPricingPort counting;
    private PortfolioResponseMapper responseMapper;
    private PortfolioSummaryService service;
    // Per-test FX series the MultiCurrencyPnlCalculator reads (empty by default -> USD/EUR frames stay empty,
    // matching the prior stub). A frame test populates it to exercise the entry-date-FX cost basis.
    private final java.util.Map<String, java.util.Map<java.time.LocalDate, java.math.BigDecimal>> fxSeriesByCode = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        counting = new CountingAssetPricingPort();
        responseMapper = new PortfolioResponseMapperImpl();
        CurrencyFrameConverter frameConverter = new CurrencyFrameConverter();
        AllocationFxFrameLoader allocationFxFrameLoader = new AllocationFxFrameLoader(
                positionRepository, derivativePositionRepository, (type, code, from, to) -> java.util.Map.of());
        RealizedPnlAllocationCalculator realizedPnlCalculator = new RealizedPnlAllocationCalculator(
                positionRepository, derivativePositionRepository, responseMapper, frameConverter, allocationFxFrameLoader);
        AllocationCalculator allocationCalculator = new AllocationCalculator(
                counting, positionRepository, derivativePositionRepository, responseMapper,
                viopCandleRepository, assetSnapshotRepository, frameConverter,
                allocationFxFrameLoader, realizedPnlCalculator);
        DerivativePricingResolver pricingResolver = new DerivativePricingResolver(viopCandleRepository, counting);
        DerivativePositionFormatter derivativeFormatter = new DerivativePositionFormatter(pricingResolver);
        DerivativeAggregationService derivativeAggregationService =
                new DerivativeAggregationService(derivativePositionRepository, pricingResolver);
        PositionRowBuilder positionRowBuilder = new PositionRowBuilder(responseMapper, assetSnapshotRepository);
        SpotAggregationService spotAggregationService = new SpotAggregationService(counting, positionRowBuilder);
        DailyAggregationService dailyAggregationService = new DailyAggregationService(
                assetSnapshotRepository, portfolioSnapshotRepository, derivativePositionRepository);
        MultiCurrencyPnlCalculator multiCurrency = new MultiCurrencyPnlCalculator(
                (type, code, from, to) -> fxSeriesByCode.getOrDefault(code, java.util.Map.of()));
        SummaryEntryFootprintBuilder summaryFootprintBuilder =
                new SummaryEntryFootprintBuilder(derivativePositionRepository, pricingResolver);
        ViopAssetAggregateService viopAssetAggregateService = new ViopAssetAggregateService(
                derivativePositionRepository, multiCurrency, pricingResolver, summaryFootprintBuilder);
        PerCurrencyFrameOverrideService frameOverrideService =
                new PerCurrencyFrameOverrideService(portfolioSnapshotRepository, performanceService);
        service = new PortfolioSummaryService(counting, positionRepository, responseMapper,
                derivativePositionRepository,
                allocationCalculator, derivativeFormatter, derivativeAggregationService, positionRowBuilder, spotAggregationService,
                dailyAggregationService, realReturnCalculator, multiCurrency, summaryFootprintBuilder, viopAssetAggregateService, frameOverrideService);
        org.mockito.Mockito.lenient().when(portfolioSnapshotRepository.findRecentByPortfolioId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findOpenByPortfolio(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.math.BigDecimal.class)))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.lenient().when(realReturnCalculator.compute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(RealReturnCalculator.RealReturnSummary.EMPTY);
        org.mockito.Mockito.lenient().when(realReturnCalculator.computeFromFootprints(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(RealReturnCalculator.RealReturnSummary.EMPTY);
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

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", null, null);

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
    void shouldExposeViopEntryAndExitDatesAtNoon_soChartMarkersLandOnCorrectDay() {
        DerivativePosition closed = longOpenPosition();
        closed.closeWith(java.time.LocalDate.of(2026, 5, 18),
                new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(closed));

        List<PositionResponse> result = service.getPositions(1L);

        assertThat(result).hasSize(1);
        PositionResponse viop = result.get(0);
        assertThat(viop.entryDate()).isEqualTo(LocalDateTime.of(2026, 4, 1, 12, 0));
        assertThat(viop.exitDate()).isEqualTo(LocalDateTime.of(2026, 5, 18, 12, 0));
    }

    @Test
    void shouldIncludeDerivativesInAllocation_whenGroupingByAssetType() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longOpenPosition()));

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", null, null);

        assertThat(allocation).extracting(AllocationItem::label).contains("VIOP");
        AllocationItem viop = allocation.stream().filter(a -> a.label().equals("VIOP")).findFirst().orElseThrow();
        assertThat(viop.valueTry()).isPositive();
    }

    @Test
    void shouldExcludeDerivatives_whenAllocationFilteredToSpotAssetType() {
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(longOpenPosition()));

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", "STOCK", null);

        assertThat(allocation).isEmpty();
    }

    @Test
    void shouldPopulateCostAndRealizedOnCashSlice_whenAllocationGroupsAllTypes() {
        counting.seedPrice("STOCK", "AKBNK.IS", new BigDecimal("70"));
        PortfolioPosition open = stubPosition(AssetType.STOCK, "AKBNK.IS",
                new BigDecimal("10"), new BigDecimal("50"));
        PortfolioPosition closed = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("20"), new BigDecimal("40"));
        closed.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(open, closed));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "assetType", null, null);

        AllocationItem cash = allocation.stream()
                .filter(a -> a.label().equals("CASH"))
                .findFirst().orElseThrow();
        assertThat(cash.costTry()).isEqualByComparingTo("800.0000");
        assertThat(cash.realizedPnlTry()).isEqualByComparingTo("400.0000");
        assertThat(cash.valueTry()).isEqualByComparingTo("1200.0000");
    }

    @Test
    void shouldAggregateRestAsOther_whenLimitExceeded() {
        PortfolioPosition p1 = stubPosition(AssetType.STOCK, "A.IS", new BigDecimal("10"), new BigDecimal("10"));
        p1.closeWith(LocalDateTime.now(), new BigDecimal("20"));
        PortfolioPosition p2 = stubPosition(AssetType.STOCK, "B.IS", new BigDecimal("10"), new BigDecimal("10"));
        p2.closeWith(LocalDateTime.now(), new BigDecimal("18"));
        PortfolioPosition p3 = stubPosition(AssetType.STOCK, "C.IS", new BigDecimal("10"), new BigDecimal("10"));
        p3.closeWith(LocalDateTime.now(), new BigDecimal("15"));
        PortfolioPosition p4 = stubPosition(AssetType.STOCK, "D.IS", new BigDecimal("10"), new BigDecimal("10"));
        p4.closeWith(LocalDateTime.now(), new BigDecimal("12"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(p1, p2, p3, p4));
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "realizedPnl", "STOCK", 3);

        assertThat(allocation).hasSize(3);
        assertThat(allocation.get(0).label()).isEqualTo("A.IS");
        assertThat(allocation.get(1).label()).isEqualTo("B.IS");
        AllocationItem other = allocation.get(2);
        assertThat(other.label()).isEqualTo("OTHER");
        assertThat(other.realizedPnlTry()).isEqualByComparingTo("70.0000");
    }

    @Test
    void shouldFilterRealizedPnlByAssetType_whenAssetTypeProvided() {
        PortfolioPosition stock = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        stock.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PortfolioPosition crypto = stubPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1"), new BigDecimal("100000"));
        crypto.closeWith(LocalDateTime.now(), new BigDecimal("150000"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(stock, crypto));
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "realizedPnl", "STOCK", null);

        assertThat(allocation).hasSize(1);
        assertThat(allocation.get(0).label()).isEqualTo("THYAO.IS");
        assertThat(allocation.get(0).assetType()).isEqualTo("STOCK");
    }

    @Test
    void shouldAggregateRealizedPnlByAssetType_whenNoAssetTypeFilter() {
        PortfolioPosition winner = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        winner.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PortfolioPosition loser = stubPosition(AssetType.STOCK, "AKBNK.IS",
                new BigDecimal("5"), new BigDecimal("80"));
        loser.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(winner, loser));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "realizedPnl", null, null);

        assertThat(allocation).hasSize(1);
        AllocationItem stock = allocation.get(0);
        assertThat(stock.label()).isEqualTo("STOCK");
        assertThat(stock.assetType()).isEqualTo("STOCK");
        assertThat(stock.realizedPnlTry()).isEqualByComparingTo("100.0000");
        assertThat(stock.costTry()).isEqualByComparingTo("800.0000");
    }

    @Test
    void shouldReturnPerAssetRealizedPnl_whenAllocationFilteredToType() {
        PortfolioPosition winner = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        winner.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PortfolioPosition loser = stubPosition(AssetType.STOCK, "AKBNK.IS",
                new BigDecimal("5"), new BigDecimal("80"));
        loser.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(winner, loser));
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "realizedPnl", "STOCK", null);

        assertThat(allocation).hasSize(2);
        AllocationItem first = allocation.get(0);
        assertThat(first.label()).isEqualTo("THYAO.IS");
        assertThat(first.realizedPnlTry()).isEqualByComparingTo("200.0000");
        AllocationItem second = allocation.get(1);
        assertThat(second.label()).isEqualTo("AKBNK.IS");
        assertThat(second.realizedPnlTry()).isEqualByComparingTo("-100.0000");
    }

    @Test
    void shouldSplitClosedPositionsByAssetCode_whenAllocationModeAssetCodeAndFilterCash() {
        PortfolioPosition closedThy = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        closedThy.closeWith(LocalDateTime.now(), new BigDecimal("60"));
        PortfolioPosition closedAkb = stubPosition(AssetType.STOCK, "AKBNK.IS",
                new BigDecimal("5"), new BigDecimal("50"));
        closedAkb.closeWith(LocalDateTime.now(), new BigDecimal("55"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(closedThy, closedAkb));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        List<AllocationItem> allocation = service.getAllocation(1L, "assetCode", "CASH", null);

        assertThat(allocation).extracting(AllocationItem::label)
                .containsExactlyInAnyOrder("THYAO.IS", "AKBNK.IS");
        AllocationItem thy = allocation.stream().filter(a -> a.label().equals("THYAO.IS")).findFirst().orElseThrow();
        assertThat(thy.assetType()).isEqualTo("STOCK");
        assertThat(thy.valueTry()).isEqualByComparingTo("600.0000");
        AllocationItem akb = allocation.stream().filter(a -> a.label().equals("AKBNK.IS")).findFirst().orElseThrow();
        assertThat(akb.valueTry()).isEqualByComparingTo("275.0000");
    }

    private ViopContract usdQuotedContract(BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol("F_XU030USD0626")
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(BigDecimal.ONE)
                .initialMargin(new BigDecimal("3500.00"))
                .currency("USD")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    @Test
    void shouldExposeDirectionAwareUsdFrameForOpenShort_withSingleFxConversion() {
        // Open SHORT on a USD-quoted future, size 1, lot 1. entryPrice 3000 TRY/unit; live price 50 USD at
        // today's 40 TRY/USD => current notional 2000 TRY (notional FELL, so the short is in profit).
        // USD frame must value cost at the ENTRY-date FX (30) and value at TODAY's FX (40), each ONCE:
        //   cost  = 3000 / 30 = 100 USD
        //   value = 2000 / 40 =  50 USD
        //   SHORT pnl = cost - value = +50 USD (positive: a short profits as notional falls)
        //   equity   = cost + pnl   = 150 USD
        // A double FX conversion (dividing the already-TRY notional by FX twice) would collapse the value
        // toward ~1 USD and read a large phantom loss; dropping the direction sign would read -50 USD.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        fxSeriesByCode.put("USD", java.util.Map.of(
                entryDay, new BigDecimal("30"),
                java.time.LocalDate.now(), new BigDecimal("40")));
        counting.seedPrice("FOREX", "USD", new BigDecimal("40"));
        DerivativePosition shortPos = DerivativePosition.builder()
                .id(70L)
                .direction(DerivativeDirection.SHORT)
                .entryDate(entryDay)
                .entryPrice(new BigDecimal("3000"))
                .quantityLot(BigDecimal.ONE)
                .viopContract(usdQuotedContract(new BigDecimal("50")))
                .build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(shortPos));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        // TRY direction-aware PnL is the headline (+1000 TRY: notional fell 3000 -> 2000 on a short).
        assertThat(summary.totalPnlTry()).isEqualByComparingTo(new BigDecimal("1000.0000"));
        com.finance.portfolio.dto.response.CurrencyFramePct usd = summary.frames().get("USD");
        assertThat(usd).isNotNull();
        assertThat(usd.totalEntry()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(usd.totalValue()).isEqualByComparingTo(new BigDecimal("150.0000"));
        assertThat(usd.totalPnl()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(usd.totalPnl()).isPositive();
    }

    @Test
    void shouldNetSameUnderlyingOppositeDirectionsInUsdFrame_whenHedgedOpenViop() {
        // Same contract + entry date, opposite directions, identical size/price: a fully hedged book. The
        // LONG and SHORT legs each move +Δ and −Δ in USD, so the net direction-aware P&L must be exactly 0
        // (no per-leg rounding residue), while the gross entry cost (2 x 100 = 200 USD) is preserved.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        fxSeriesByCode.put("USD", java.util.Map.of(
                entryDay, new BigDecimal("30"),
                java.time.LocalDate.now(), new BigDecimal("40")));
        counting.seedPrice("FOREX", "USD", new BigDecimal("40"));
        DerivativePosition longLeg = DerivativePosition.builder()
                .id(71L).direction(DerivativeDirection.LONG)
                .entryDate(entryDay).entryPrice(new BigDecimal("3000")).quantityLot(BigDecimal.ONE)
                .viopContract(usdQuotedContract(new BigDecimal("50"))).build();
        DerivativePosition shortLeg = DerivativePosition.builder()
                .id(72L).direction(DerivativeDirection.SHORT)
                .entryDate(entryDay).entryPrice(new BigDecimal("3000")).quantityLot(BigDecimal.ONE)
                .viopContract(usdQuotedContract(new BigDecimal("50"))).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg, shortLeg));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        com.finance.portfolio.dto.response.CurrencyFramePct usd = summary.frames().get("USD");
        assertThat(usd).isNotNull();
        assertThat(usd.totalEntry()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(usd.totalPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldNetSameSymbolHedgeDailyToZero_whenSummingEveryLatestRowPerAsset() {
        // A LONG+SHORT hedge on ONE symbol is stored one daily-snapshot row per lot at the same instant. The
        // single-row findLatestPerAsset returned only one leg, so a net-0 hedge's Günlük K/Z read that leg's
        // ₺769 move; summing EVERY row at the symbol's latest instant (findLatestRowsPerAsset) nets it to 0.
        DerivativePosition longLeg = DerivativePosition.builder()
                .id(81L).direction(DerivativeDirection.LONG)
                .entryDate(java.time.LocalDate.of(2026, 4, 1)).entryPrice(new BigDecimal("35.20"))
                .quantityLot(BigDecimal.ONE).viopContract(sampleContract(new BigDecimal("35.50"))).build();
        DerivativePosition shortLeg = DerivativePosition.builder()
                .id(82L).direction(DerivativeDirection.SHORT)
                .entryDate(java.time.LocalDate.of(2026, 4, 1)).entryPrice(new BigDecimal("35.20"))
                .quantityLot(BigDecimal.ONE).viopContract(sampleContract(new BigDecimal("35.50"))).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg, shortLeg));
        PortfolioAssetDailySnapshot longRow = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.VIOP).assetCode("F_USDTRY0626")
                .dailyPnlTry(new BigDecimal("769.0860")).dailyPnlPercent(new BigDecimal("0.19"))
                .marketValueTry(new BigDecimal("400000.0000")).build();
        PortfolioAssetDailySnapshot shortRow = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.VIOP).assetCode("F_USDTRY0626")
                .dailyPnlTry(new BigDecimal("-769.0860")).dailyPnlPercent(new BigDecimal("-0.19"))
                .marketValueTry(new BigDecimal("400000.0000")).build();
        when(assetSnapshotRepository.findLatestRowsPerAsset(1L)).thenReturn(List.of(longRow, shortRow));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.dailyPnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldOverrideForeignDailyWithPerDateFrameDelta_notTodaysSingleRate() {
        // The USD daily K/Z must be the per-date frame DELTA (today − prior snapshot), so a USD/EUR-quoted VİOP
        // reads its native day-move instead of the TRY daily ÷ today's single rate (which is FX-contaminated).
        // Stub the per-date cumulative pnl: USD 100 today vs 70 prior → USD daily = +30, independent of TRY.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        fxSeriesByCode.put("USD", java.util.Map.of(
                entryDay, new BigDecimal("30"), java.time.LocalDate.now(), new BigDecimal("40")));
        ViopContract c = ViopContract.builder().symbol("F_USDX0626").kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY).contractSize(BigDecimal.ONE).currency("TRY")
                .lastPrice(new BigDecimal("120")).active(true).build();
        DerivativePosition longLeg = DerivativePosition.builder().id(90L).direction(DerivativeDirection.LONG)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE)
                .viopContract(c).build();
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg));
        com.finance.portfolio.model.PortfolioDailySnapshot today = com.finance.portfolio.model.PortfolioDailySnapshot
                .builder().snapshotDate(java.time.LocalDate.now()).createdAt(java.time.LocalDateTime.now())
                .totalValueTry(new BigDecimal("4800")).build();
        com.finance.portfolio.model.PortfolioDailySnapshot prior = com.finance.portfolio.model.PortfolioDailySnapshot
                .builder().snapshotDate(java.time.LocalDate.now().minusDays(1)).createdAt(java.time.LocalDateTime.now().minusDays(1))
                .totalValueTry(new BigDecimal("4700")).build();
        when(portfolioSnapshotRepository.findRecentByPortfolioId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(today, prior));
        when(performanceService.dailyPnlByCcy(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of(
                        today.getSnapshotDate(), java.util.Map.of("USD", new BigDecimal("100")),
                        prior.getSnapshotDate(), java.util.Map.of("USD", new BigDecimal("70"))));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        com.finance.portfolio.dto.response.CurrencyFramePct usd = summary.frames().get("USD");
        assertThat(usd).isNotNull();
        assertThat(usd.dailyPnl()).isEqualByComparingTo("30");   // per-date delta 100 − 70, not TRY ÷ today's rate
    }

    @Test
    void shouldExcludeViopFromRealReturnBase_soPureViopFeedsEmptySpotFootprints() {
        // REAL (inflation-adjusted) return is a SPOT purchasing-power metric. A pure-VİOP book (here a net-0
        // hedge) must NOT feed its gross notional into the CPI base — that produced the spurious real loss
        // (CPI × notional). So the real calculator must receive an EMPTY (spot-only) footprint list → no REEL row.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        ViopContract tryHedge = ViopContract.builder()
                .symbol("F_HEDGE0626").kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY).contractSize(BigDecimal.ONE)
                .currency("TRY").lastPrice(new BigDecimal("120")).active(true).build();
        DerivativePosition longLeg = DerivativePosition.builder().id(80L).direction(DerivativeDirection.LONG)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE).viopContract(tryHedge).build();
        DerivativePosition shortLeg = DerivativePosition.builder().id(81L).direction(DerivativeDirection.SHORT)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE).viopContract(tryHedge).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());   // no spot lots
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg, shortLeg));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RealReturnCalculator.EntryFootprint>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);

        service.getSummary(1L, null);

        org.mockito.Mockito.verify(realReturnCalculator)
                .computeFromFootprints(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue()).isEmpty();   // VİOP excluded → no real-return base → REEL row suppressed
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
                service.getPositionsPaged(1L, "THY", null, "profitAmount", "desc", null, 0, 10);

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
                service.getPositionsPaged(1L, "ZZZZ", null, "assetCode", "asc", null, 0, 10);

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
    void shouldFallBackToPortfolioSnapshotDelta_whenAssetSnapshotsHaveNullDailyPnl() {
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("100000"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.CRYPTO, "bitcoin",
                        new BigDecimal("1"), new BigDecimal("98000"))));
        when(assetSnapshotRepository.findLatestRowsPerAsset(1L)).thenReturn(List.of());
        com.finance.portfolio.model.PortfolioDailySnapshot latest = com.finance.portfolio.model.PortfolioDailySnapshot.builder()
                .totalValueTry(new BigDecimal("100000.0000")).build();
        com.finance.portfolio.model.PortfolioDailySnapshot prior = com.finance.portfolio.model.PortfolioDailySnapshot.builder()
                .totalValueTry(new BigDecimal("97500.0000")).build();
        when(portfolioSnapshotRepository.findRecentByPortfolioId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(latest, prior));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.dailyPnlTry()).isEqualByComparingTo(new BigDecimal("2500.0000"));
    }

    @Test
    void shouldIgnoreStaleSnapshotsOfDeletedAssets_whenAggregatingDaily() {
        // AKBNK is still held; GARAN was deleted but its latest per-asset snapshot row lingers. findLatestPerAsset
        // returns BOTH, so without a live-holding gate the daily card sums GARAN's stale 5000 into a phantom.
        counting.seedPrice("STOCK", "AKBNK.IS", new BigDecimal("110"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.STOCK, "AKBNK.IS",
                        new BigDecimal("10"), new BigDecimal("100"))));
        PortfolioAssetDailySnapshot live = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.STOCK).assetCode("AKBNK.IS")
                .dailyPnlTry(new BigDecimal("100.0000")).dailyPnlPercent(new BigDecimal("10"))
                .marketValueTry(new BigDecimal("1100.0000")).build();
        PortfolioAssetDailySnapshot stale = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.STOCK).assetCode("GARAN.IS")
                .dailyPnlTry(new BigDecimal("5000.0000")).dailyPnlPercent(new BigDecimal("10"))
                .marketValueTry(new BigDecimal("55000.0000")).build();
        when(assetSnapshotRepository.findLatestRowsPerAsset(1L)).thenReturn(List.of(live, stale));

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.dailyPnlTry()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void shouldReturnZeroDailyPnl_whenPortfolioFullyLiquidated() {
        // No open spot lots and no open derivatives: nothing moved today. Even if stale per-asset snapshots
        // exist, the daily card must read 0 — not a phantom from a liquidated asset's last row.
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.dailyPnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnNullDailyPnl_whenNoFallbackHistoryExists() {
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("100000"));
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.CRYPTO, "bitcoin",
                        new BigDecimal("1"), new BigDecimal("98000"))));
        when(assetSnapshotRepository.findLatestRowsPerAsset(1L)).thenReturn(List.of());

        PortfolioSummaryResponse summary = service.getSummary(1L, null);

        assertThat(summary.dailyPnlTry()).isNull();
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

    @Test
    void shouldExposeNativeCurrencyFrame_soForeignHoldingShowsOwnReturnNotLiraReturn() {
        // 12 USD bought in 2001 at 0.6860 TRY/unit (=$1 then), now 45.9522 TRY/unit. In TRY this is the
        // lira-devaluation return (~+6598%); in USD it is still 12 USD, so the USD frame must read ~0% / ~$0.
        // This is the asset-detail regression: the page showed the TRY % beside a "$" label.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2001, 2, 15);
        fxSeriesByCode.put("USD", java.util.Map.of(
                entryDay, new BigDecimal("0.6860"),
                java.time.LocalDate.now(), new BigDecimal("45.9522")));
        counting.seedPrice("FOREX", "USD", new BigDecimal("45.9522"));
        PortfolioPosition usd = PortfolioPosition.builder()
                .assetType(AssetType.FOREX).assetCode("USD")
                .quantity(new BigDecimal("12")).entryPrice(new BigDecimal("0.6860"))
                .entryDate(entryDay.atStartOfDay()).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(usd));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "FOREX", "USD");

        // TRY frame keeps the huge lira-devaluation return.
        assertThat(result.pnlPercent()).isGreaterThan(new BigDecimal("6000"));
        // USD frame: same 12 USD in and out -> ~0% and ~$0, never the lira's +6598%.
        com.finance.portfolio.dto.response.CurrencyFramePct usdFrame = result.frames().get("USD");
        assertThat(usdFrame).isNotNull();
        assertThat(usdFrame.pnlPercent().abs()).isLessThan(new BigDecimal("0.5"));
        assertThat(usdFrame.totalPnl().abs()).isLessThan(new BigDecimal("0.10"));
    }

    @Test
    void shouldNetHedgedViopToZeroUsdPnl_whenAggregatingPerSymbol() {
        // Balanced LONG+SHORT hedge on one TRY-quoted contract, size 1, lot 1: LONG entry 100, SHORT entry
        // 100, live 120. Direction-aware PnL nets to ~0 (+20 LONG, −20 SHORT), NOT the direction-blind
        // value−cost. The USD frame must reproduce that net via per-leg direction correction at entry FX
        // (30) / today FX (40): cost 200/30+... value 240/40 + SHORT correction → USD totalPnl == 0.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        fxSeriesByCode.put("USD", java.util.Map.of(
                entryDay, new BigDecimal("30"),
                java.time.LocalDate.now(), new BigDecimal("40")));
        ViopContract tryHedge = ViopContract.builder()
                .symbol("F_HEDGE0626").kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY).contractSize(BigDecimal.ONE)
                .currency("TRY").lastPrice(new BigDecimal("120")).active(true).build();
        DerivativePosition longLeg = DerivativePosition.builder()
                .id(80L).direction(DerivativeDirection.LONG)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE)
                .viopContract(tryHedge).build();
        DerivativePosition shortLeg = DerivativePosition.builder()
                .id(81L).direction(DerivativeDirection.SHORT)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE)
                .viopContract(tryHedge).build();
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg, shortLeg));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "VIOP", "F_HEDGE0626");

        // Direction-aware aggregate: hedge nets to 0 TRY, entry basis is the gross 200.
        assertThat(result.totalPnlTry()).isEqualByComparingTo("0.0000");
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("200.0000");
        // USD frame reproduces the net (≈0), NOT the direction-blind value−cost which would read a loss.
        com.finance.portfolio.dto.response.CurrencyFramePct usd = result.frames().get("USD");
        assertThat(usd).isNotNull();
        assertThat(usd.totalPnl()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldSplitHedgeAggregatePerDirection_whenDirectionFilterGiven() {
        // Same balanced hedge (LONG entry 100, SHORT entry 100, live 120, size 1, lot 1). The detail page now
        // requests each leg separately: LONG-only reads +20 TRY (a profiting long), SHORT-only reads −20 TRY (a
        // losing short), and the two reconstitute the netted ~0 blend — so a hedge is no longer one spot blob.
        java.time.LocalDate entryDay = java.time.LocalDate.of(2026, 4, 1);
        ViopContract tryHedge = ViopContract.builder()
                .symbol("F_HEDGE0626").kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY).contractSize(BigDecimal.ONE)
                .currency("TRY").lastPrice(new BigDecimal("120")).active(true).build();
        DerivativePosition longLeg = DerivativePosition.builder()
                .id(80L).direction(DerivativeDirection.LONG)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE)
                .viopContract(tryHedge).build();
        DerivativePosition shortLeg = DerivativePosition.builder()
                .id(81L).direction(DerivativeDirection.SHORT)
                .entryDate(entryDay).entryPrice(new BigDecimal("100")).quantityLot(BigDecimal.ONE)
                .viopContract(tryHedge).build();
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longLeg, shortLeg));

        com.finance.portfolio.dto.response.AssetAggregateResponse longOnly =
                service.getAssetAggregate(1L, "VIOP", "F_HEDGE0626", "LONG");
        com.finance.portfolio.dto.response.AssetAggregateResponse shortOnly =
                service.getAssetAggregate(1L, "VIOP", "F_HEDGE0626", "short");   // case-insensitive

        assertThat(longOnly.lotCount()).isEqualTo(1);
        assertThat(longOnly.totalPnlTry()).isEqualByComparingTo("20.0000");
        assertThat(shortOnly.lotCount()).isEqualTo(1);
        assertThat(shortOnly.totalPnlTry()).isEqualByComparingTo("-20.0000");
        assertThat(longOnly.totalPnlTry().add(shortOnly.totalPnlTry())).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldReturnNonEmptyFrameForSingleOpenLongViop_soDetailGetsBackendFrame() {
        // Regression: a VIOP symbol used to fall through the spot-only path and return frames = Map.of(),
        // forcing a frontend shortcut. A single open LONG must now carry at least the TRY frame.
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(longOpenPosition()));

        com.finance.portfolio.dto.response.AssetAggregateResponse result =
                service.getAssetAggregate(1L, "VIOP", "F_USDTRY0626");

        assertThat(result.frames()).isNotEmpty();
        assertThat(result.frames().get("TRY")).isNotNull();
        assertThat(result.lotCount()).isEqualTo(1);
        // LONG 35.20 -> 35.50, size 1000: +300 TRY direction-aware PnL.
        assertThat(result.totalPnlTry()).isEqualByComparingTo("300.0000");
    }

    // Bug 2: the daily-% prior baseline is each asset's yesterday value (priorQty*priorPrice), recovered
    // from its stored daily delta — NOT marketValue/totalValue, which on an add-to-existing-asset day folds
    // the new lot's full value in and dilutes the %.
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "10,   10,   110,  100",   // normal / add-to-existing move: priorValue = dailyPnl*100/pct
            "0,    0,    50,   50",    // flat price (pct 0): priorValue = marketValue - dailyPnl
            "5,    null, 80,   0",     // no prior baseline (pct null): contributes 0
    })
    void priorValueOf_recoversYesterdaysAssetValue(BigDecimal dailyPnl, BigDecimal dailyPct,
                                                   BigDecimal marketValue, BigDecimal expected) {
        PortfolioAssetDailySnapshot snap = PortfolioAssetDailySnapshot.builder()
                .dailyPnlTry(dailyPnl)
                .dailyPnlPercent(dailyPct)
                .marketValueTry(marketValue)
                .build();

        assertThat(DailyAggregationService.priorValueOf(snap)).isEqualByComparingTo(expected);
    }
}
