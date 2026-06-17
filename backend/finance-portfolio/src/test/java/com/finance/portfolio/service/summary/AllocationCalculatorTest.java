package com.finance.portfolio.service.summary;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationCalculatorTest {

    private static final Long PORTFOLIO_ID = 100L;

    @Mock private AssetPricingPort pricingPort;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private PortfolioResponseMapper mapper;
    @Mock private com.finance.market.core.service.HistoricalPricingPort historicalPricingPort;
    @Mock private com.finance.market.viop.repository.ViopCandleRepository viopCandleRepository;
    @Mock private com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    private AllocationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AllocationCalculator(pricingPort, positionRepository,
                derivativePositionRepository, mapper, historicalPricingPort,
                viopCandleRepository, assetSnapshotRepository, new CurrencyFrameConverter());
        lenient().when(mapper.toAllocationItem(anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new AllocationItem(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), inv.getArgument(4), inv.getArgument(5)));
        lenient().when(mapper.toAllocationItem(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new AllocationItem(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), inv.getArgument(4), inv.getArgument(5),
                        inv.getArgument(6), inv.getArgument(7)));
    }

    private PortfolioPosition openSpot(AssetType type, String code, String qty, String entry) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(new BigDecimal(qty))
                .entryPrice(new BigDecimal(entry))
                .entryDate(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
    }

    private PortfolioPosition closedSpot(AssetType type, String code, String qty, String entry, String exit) {
        PortfolioPosition pos = openSpot(type, code, qty, entry);
        pos.closeWith(LocalDateTime.of(2024, 6, 1, 12, 0), new BigDecimal(exit));
        return pos;
    }

    private ViopContract contract(String symbol, ViopContractKind kind, String size,
                                    String last, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(kind)
                .contractSize(size != null ? new BigDecimal(size) : null)
                .lastPrice(last != null ? new BigDecimal(last) : null)
                .currency(currency)
                .active(true)
                .build();
    }

    private DerivativePosition openDerivative(ViopContract c, DerivativeDirection dir, String entry, String qty) {
        return DerivativePosition.builder()
                .viopContract(c)
                .direction(dir)
                .entryDate(java.time.LocalDate.of(2024, 1, 1))
                .entryPrice(new BigDecimal(entry))
                .quantityLot(new BigDecimal(qty))
                .build();
    }

    private DerivativePosition closedDerivative(ViopContract c, DerivativeDirection dir,
                                                  String entry, String qty, String close) {
        DerivativePosition dp = openDerivative(c, dir, entry, qty);
        dp.setCloseDate(java.time.LocalDate.of(2024, 7, 1));
        dp.setClosePrice(new BigDecimal(close));
        return dp;
    }

    @Test
    void shouldReturnEmptyList_whenPortfolioHasNoPositionsOrDerivatives() {
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAttachPerCurrencyEquity_toOpenShortDerivativeSlice() {
        // Open SHORT: entry notional 2000 TRY (entry 100 × size 10 × 2 lots); current price 90 → notional 1800
        // (price fell, short profits +200) → equity 2200. USD/TRY rose 10→12. The donut slice must carry per-date
        // frames so the frontend shows cost@entryFX 200 + PnL +50 = $250 equity — NOT 2200÷12 = $183 (today spot).
        ViopContract c = contract("F_XS", ViopContractKind.FUTURE, "10", "90", "TRY");
        DerivativePosition shortPos = openDerivative(c, DerivativeDirection.SHORT, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(shortPos));
        java.util.Map<java.time.LocalDate, BigDecimal> usdFx = new java.util.HashMap<>();
        usdFx.put(java.time.LocalDate.of(2024, 1, 1), new BigDecimal("10"));   // entry-date FX
        usdFx.put(java.time.LocalDate.of(2024, 6, 1), new BigDecimal("12"));   // latest ⇒ today's FX
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any())).thenReturn(usdFx);

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        AllocationItem viop = result.stream().filter(i -> "F_XS".equals(i.label())).findFirst().orElseThrow();
        assertThat(viop.valueTry()).isEqualByComparingTo("2200");                       // TRY equity
        assertThat(viop.costByCurrency().get("USD")).isEqualByComparingTo("200");       // cost @ entry FX
        assertThat(viop.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("50"); // direction-aware PnL → $250 equity
    }

    @Test
    void shouldUseEntryDateRateForOpenDerivativeCost_whenEntryPredatesLoadedFxWindow() {
        // Open LONG entered 2024-01-01 (entry notional 100 × size 10 × 2 lots = 2000 TRY). With no closed
        // positions/derivatives the FX-series window previously anchored on today, so this open entry fell
        // OUTSIDE the loaded window: convertToFrames' floorEntry was null and it fell back to lastEntry =
        // today's rate (20), reading cost as 2000/20 = $100. The fix extends the window back to the OLDEST
        // entry across open AND closed positions, so the entry-date rate (10) is in the series and the cost
        // leg converts at it: 2000/10 = $200. The mock filters by the requested window like the real port.
        ViopContract c = contract("F_OLD", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition openLong = openDerivative(c, DerivativeDirection.LONG, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(openLong));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());
        java.time.LocalDate entryDate = java.time.LocalDate.of(2024, 1, 1);
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any()))
                .thenAnswer(inv -> {
                    java.time.LocalDate from = inv.getArgument(2);
                    java.time.LocalDate to = inv.getArgument(3);
                    java.util.Map<java.time.LocalDate, BigDecimal> out = new java.util.HashMap<>();
                    if (!from.isAfter(entryDate) && !to.isBefore(entryDate)) {
                        out.put(entryDate, new BigDecimal("10"));   // entry-date FX, only present if window reaches back
                    }
                    out.put(java.time.LocalDate.now(), new BigDecimal("20")); // today's spot ⇒ lastEntry fallback
                    return out;
                });

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        AllocationItem viop = result.stream().filter(i -> "F_OLD".equals(i.label())).findFirst().orElseThrow();
        assertThat(viop.costByCurrency().get("USD")).isEqualByComparingTo("200"); // entryTry/entryRate, not /todayRate
    }

    @Test
    void shouldAttachPerCurrencyFramesToOpenDerivative_whenAssetTypeFilterIsVIOP() {
        // Asset-type filter (VIOP) does NOT emit a CASH bucket, so the FX series was previously skipped and the
        // open derivative's per-currency cost frame came back empty. The fix loads the series whenever
        // derivatives are in scope, so the VIOP slice carries cost@entry-FX even under a filter.
        ViopContract c = contract("F_FILT", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition openLong = openDerivative(c, DerivativeDirection.LONG, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(openLong));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());
        java.util.Map<java.time.LocalDate, BigDecimal> usdFx = new java.util.HashMap<>();
        usdFx.put(java.time.LocalDate.of(2024, 1, 1), new BigDecimal("10"));
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any())).thenReturn(usdFx);

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "VIOP", null);

        AllocationItem viop = result.stream().filter(i -> "F_FILT".equals(i.label())).findFirst().orElseThrow();
        assertThat(viop.costByCurrency()).containsKey("USD");
        assertThat(viop.costByCurrency().get("USD")).isEqualByComparingTo("200");
    }

    @Test
    void shouldGroupOpenSpotByAssetCode_whenNoMode() {
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        PortfolioPosition crypto = openSpot(AssetType.CRYPTO, "BTC", "1", "50000");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock, crypto));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("120"),
                new AssetKey(MarketType.CRYPTO, "BTC"), new BigDecimal("60000")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).containsExactly("BTC", "AKBNK");
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("60000");
        assertThat(result.get(1).valueTry()).isEqualByComparingTo("1200");
        assertThat(result.get(0).percent().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void shouldGroupOpenSpotByAssetType_whenGroupByTypeMode() {
        PortfolioPosition stock1 = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        PortfolioPosition stock2 = openSpot(AssetType.STOCK, "THYAO", "5", "200");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock1, stock2));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("110"),
                new AssetKey(MarketType.STOCK, "THYAO"), new BigDecimal("220")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "assetType", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("STOCK");
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("2200");
    }

    @Test
    void shouldFallbackToEntryValue_whenLivePriceAndSnapshotMissing() {
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());
        // Snapshot repository returns empty → final fallback is entryValue (10 × 100 = 1000).
        // Earlier "zero on missing price" behaviour silently dropped a real holding from the
        // pie while the summary card still summed it via its own snapshot fallback — produced
        // the visible card-vs-pie gap.

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("1000");
        assertThat(result.get(0).percent()).isEqualByComparingTo("100");
    }

    @Test
    void shouldAppendCashBucketFromClosedSpot_whenNoFilter() {
        PortfolioPosition closed = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("CASH");
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("1500");
        assertThat(result.get(0).costTry()).isEqualByComparingTo("1000");
        assertThat(result.get(0).realizedPnlTry()).isEqualByComparingTo("500");
    }

    @Test
    void shouldSkipClosedSpotWithoutExitPrice_whenComputingAllocation() {
        PortfolioPosition closedNoExit = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        closedNoExit.closeWith(LocalDateTime.of(2024, 6, 1, 12, 0), null);
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedNoExit));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSplitClosedByAsset_whenCashOnlyFilterWithoutGroupByType() {
        PortfolioPosition closed = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "CASH", null);

        assertThat(result).extracting(AllocationItem::label).containsExactly("AKBNK");
    }

    @Test
    void shouldEmitCashBucket_whenCashOnlyFilterWithGroupByTypeMode() {
        PortfolioPosition closed = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "assetType", "CASH", null);

        assertThat(result).extracting(AllocationItem::label).containsExactly("CASH");
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("1500");
    }

    @Test
    void shouldFilterPositionsByAssetType_whenFilterMatches() {
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        PortfolioPosition crypto = openSpot(AssetType.CRYPTO, "BTC", "1", "50000");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock, crypto));
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "STOCK", null);

        assertThat(result).extracting(AllocationItem::label).containsOnly("AKBNK");
    }

    @Test
    void shouldThrowBadRequest_whenAssetTypeFilterIsInvalidEnum() {
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> calculator.compute(PORTFOLIO_ID, null, "NOT_AN_ENUM", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldNotEmitCashBucket_whenFilterIsSpecificAssetType() {
        PortfolioPosition closed = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        PortfolioPosition open = openSpot(AssetType.STOCK, "THYAO", "5", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed, open));
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "THYAO"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "STOCK", null);

        assertThat(result).extracting(AllocationItem::label).doesNotContain("CASH");
    }

    @Test
    void shouldIncludeOpenDerivativeMarketValue_whenNoFilter() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "120", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("XU030F");
        AllocationItem item = result.stream().filter(i -> "XU030F".equals(i.label())).findFirst().orElseThrow();
        assertThat(item.valueTry()).isEqualByComparingTo("2400");
    }

    @Test
    void shouldSkipDerivativeWithoutContract_whenIterating() {
        DerivativePosition dp = DerivativePosition.builder()
                .direction(DerivativeDirection.LONG)
                .entryDate(java.time.LocalDate.now())
                .entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .build();
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGroupDerivativeAsVIOP_whenGroupByTypeMode() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "assetType", null, null);

        assertThat(result).extracting(AllocationItem::label).contains("VIOP");
    }

    @ParameterizedTest
    @CsvSource({"FUTURE", "OPTION"})
    void shouldThrowBadRequest_whenAssetTypeFilterIsDerivativeKind(String filter) {
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> calculator.compute(PORTFOLIO_ID, null, filter, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldIncludeVIOPDerivative_whenAssetTypeFilterIsVIOP() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "VIOP", null);

        assertThat(result).extracting(AllocationItem::label).contains("XU030F");
    }

    @Test
    void shouldSkipDerivativeWithNullEntryNotional_whenContractIncomplete() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(java.time.LocalDate.now())
                .quantityLot(BigDecimal.ONE)
                .build();
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAddClosedDerivativeToCashBucket_whenNoFilterAndClosed() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.LONG, "100", "2", "120");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("CASH");
    }

    @Test
    void shouldConvertOpenDerivativePriceToTry_whenCurrencyIsNotTry() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "100", "USD");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "90", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(new BigDecimal("30"));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("F_XAUUSD0625");
    }

    @Test
    void shouldFallbackToNativeWhenFxRateMissing_whenCurrencyIsNotTry() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "100", "USD");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "90", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(null);

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("F_XAUUSD0625");
    }

    @Test
    void shouldReturnRealizedPnlAllocation_whenModeIsRealizedPnl() {
        PortfolioPosition closed1 = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        PortfolioPosition closed2 = closedSpot(AssetType.STOCK, "THYAO", "5", "100", "80");
        PortfolioPosition open = openSpot(AssetType.CRYPTO, "BTC", "1", "50000");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed1, closed2, open));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", null, null);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).realizedPnlTry()).isNotNull();
        assertThat(result).extracting(AllocationItem::label).doesNotContain("BTC");
    }

    @Test
    void shouldGroupRealizedPnlByCode_whenAssetTypeFilterProvided() {
        PortfolioPosition closed1 = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        PortfolioPosition closed2 = closedSpot(AssetType.STOCK, "THYAO", "5", "100", "80");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed1, closed2));
        lenient().when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", "STOCK", null);

        assertThat(result).extracting(AllocationItem::label).containsExactlyInAnyOrder("AKBNK", "THYAO");
    }

    @Test
    void shouldSkipRealizedPnlForOpenPosition_whenModeIsRealizedPnl() {
        PortfolioPosition open = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(open));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIncludeClosedDerivativeInRealizedPnl_whenNoFilter() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "120", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.LONG, "100", "2", "120");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", null, null);

        assertThat(result).extracting(AllocationItem::label).contains("VIOP");
    }

    @Test
    void shouldReportClosedShortDerivativeProfit_inForeignCurrency_whenNoFilter() {
        // SHORT closed at a profit (price fell 100→90): entry notional 2000 TRY, realized +200, close notional
        // 1800 TRY. USD/TRY rose 10→12 between entry and close. Pricing proceeds (2200) at the close FX and
        // subtracting cost at the entry FX gives 2200/12 − 2000/10 = −16.67 (a LOSS); the direction-aware
        // value is −1×(1800/12 − 2000/10) = +50 (a PROFIT) — what the donut must show in USD.
        ViopContract c = contract("XU030S", ViopContractKind.FUTURE, "10", "90", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.SHORT, "100", "2", "90");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        java.util.Map<java.time.LocalDate, BigDecimal> usdFx = new java.util.HashMap<>();
        usdFx.put(java.time.LocalDate.of(2024, 1, 1), new BigDecimal("10"));   // entry-date FX
        usdFx.put(java.time.LocalDate.of(2024, 7, 1), new BigDecimal("12"));   // close-date FX (TRY weakened)
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any())).thenReturn(usdFx);

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", null, null);

        AllocationItem viop = result.stream().filter(i -> "VIOP".equals(i.label())).findFirst().orElseThrow();
        assertThat(viop.realizedPnlTry()).isEqualByComparingTo("200");           // TRY profit (direction-aware)
        assertThat(viop.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("50");  // USD profit, not −16.67
    }

    @Test
    void shouldFilterRealizedPnlToVIOPOnly_whenAssetTypeFilterIsVIOP() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "120", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.LONG, "100", "2", "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "realizedPnl", "VIOP", null);

        assertThat(result).extracting(AllocationItem::label).contains("XU030F");
    }

    @Test
    void shouldApplyLimit_whenLimitIsLessThanItemCount() {
        PortfolioPosition a = openSpot(AssetType.STOCK, "A", "1", "100");
        PortfolioPosition b = openSpot(AssetType.STOCK, "B", "1", "100");
        PortfolioPosition c = openSpot(AssetType.STOCK, "C", "1", "100");
        PortfolioPosition d = openSpot(AssetType.STOCK, "D", "1", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(a, b, c, d));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "A"), new BigDecimal("400"),
                new AssetKey(MarketType.STOCK, "B"), new BigDecimal("300"),
                new AssetKey(MarketType.STOCK, "C"), new BigDecimal("200"),
                new AssetKey(MarketType.STOCK, "D"), new BigDecimal("100")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(2).label()).isEqualTo("OTHER");
        assertThat(result.get(2).valueTry()).isEqualByComparingTo("300");
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "-1"
    })
    void shouldReturnFullList_whenLimitIsZeroOrNegative(int limit) {
        PortfolioPosition a = openSpot(AssetType.STOCK, "A", "1", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(a));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "A"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, limit);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnAllItems_whenLimitGreaterThanSize() {
        PortfolioPosition a = openSpot(AssetType.STOCK, "A", "1", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(a));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "A"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, 10);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldNotConvertDerivativeOpenPriceWhenCurrencyIsTry_whenCurrencyTry() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "100", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("XU030F");
    }

    @Test
    void shouldSplitClosedDerivativeBySymbol_whenCashOnlyFilter() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "120", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.LONG, "100", "2", "120");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "CASH", null);

        assertThat(result).extracting(AllocationItem::label).contains("XU030F");
    }

    @Test
    void shouldNotIncludeDerivativesWhenSpotFilterApplies_whenStockFilter() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "1");
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "1", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock));
        lenient().when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        when(pricingPort.getPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "STOCK", null);

        assertThat(result).extracting(AllocationItem::label).containsOnly("AKBNK");
    }
}
