package com.finance.portfolio.service;

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

    private AllocationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AllocationCalculator(pricingPort, positionRepository,
                derivativePositionRepository, mapper, historicalPricingPort);
        lenient().when(mapper.toAllocationItem(anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new AllocationItem(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), inv.getArgument(4), inv.getArgument(5)));
        lenient().when(mapper.toAllocationItem(anyString(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new AllocationItem(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3), inv.getArgument(4), inv.getArgument(5),
                        inv.getArgument(6)));
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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGroupOpenSpotByAssetCode_whenNoMode() {
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        PortfolioPosition crypto = openSpot(AssetType.CRYPTO, "BTC", "1", "50000");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock, crypto));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("110"),
                new AssetKey(MarketType.STOCK, "THYAO"), new BigDecimal("220")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, "assetType", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("STOCK");
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("2200");
    }

    @Test
    void shouldUseZeroMarketValue_whenPriceMissing() {
        PortfolioPosition stock = openSpot(AssetType.STOCK, "AKBNK", "10", "100");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(stock));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).valueTry()).isEqualByComparingTo("0");
        assertThat(result.get(0).percent()).isEqualByComparingTo("0");
    }

    @Test
    void shouldAppendCashBucketFromClosedSpot_whenNoFilter() {
        PortfolioPosition closed = closedSpot(AssetType.STOCK, "AKBNK", "10", "100", "150");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGroupDerivativeAsVIOP_whenGroupByTypeMode() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "100", "2");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAddClosedDerivativeToCashBucket_whenNoFilterAndClosed() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", "100", "TRY");
        DerivativePosition dp = closedDerivative(c, DerivativeDirection.LONG, "100", "2", "120");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("CASH");
    }

    @Test
    void shouldConvertOpenDerivativePriceToTry_whenCurrencyIsNotTry() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "100", "USD");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "90", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());
        when(pricingPort.getExitPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(new BigDecimal("30"));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, null, null);

        assertThat(result).extracting(AllocationItem::label).contains("F_XAUUSD0625");
    }

    @Test
    void shouldFallbackToNativeWhenFxRateMissing_whenCurrencyIsNotTry() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "100", "USD");
        DerivativePosition dp = openDerivative(c, DerivativeDirection.LONG, "90", "1");
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dp));
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());
        when(pricingPort.getExitPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(null);

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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
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
        lenient().when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of());

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
        when(pricingPort.getExitPricesTry(anyCollection())).thenReturn(Map.of(
                new AssetKey(MarketType.STOCK, "AKBNK"), new BigDecimal("110")
        ));

        List<AllocationItem> result = calculator.compute(PORTFOLIO_ID, null, "STOCK", null);

        assertThat(result).extracting(AllocationItem::label).containsOnly("AKBNK");
    }
}
