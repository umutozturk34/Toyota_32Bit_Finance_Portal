package com.finance.portfolio.service.pricing;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.shared.service.AssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativePositionFormatterTest {

    @Mock private ViopCandleRepository viopCandleRepository;
    @Mock private AssetPricingPort pricingPort;

    private DerivativePositionFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DerivativePositionFormatter(
                new DerivativePricingResolver(viopCandleRepository, pricingPort));
    }

    private ViopContract contract(String symbol, ViopContractKind kind, String size, String last,
                                    String currency, String strike, String displayName) {
        return ViopContract.builder()
                .symbol(symbol)
                .displayName(displayName)
                .kind(kind)
                .contractSize(size != null ? new BigDecimal(size) : null)
                .lastPrice(last != null ? new BigDecimal(last) : null)
                .currency(currency)
                .strikePrice(strike != null ? new BigDecimal(strike) : null)
                .expiryDate(LocalDate.of(2026, 12, 31))
                .initialMargin(new BigDecimal("10"))
                .active(true)
                .build();
    }

    private DerivativePosition openPosition(ViopContract c, DerivativeDirection dir, String entry, String qty) {
        return DerivativePosition.builder()
                .id(1L)
                .viopContract(c)
                .direction(dir)
                .entryDate(LocalDate.of(2024, 1, 1))
                .entryPrice(new BigDecimal(entry))
                .quantityLot(new BigDecimal(qty))
                .build();
    }

    @Test
    void shouldReturnNull_whenContractIsNull() {
        DerivativePosition dp = DerivativePosition.builder()
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        PositionResponse result = formatter.toPositionResponse(dp);

        assertThat(result).isNull();
    }

    @Test
    void shouldBuildOpenLongFutureResponse_whenLatestCandleProvidesPrice() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", null, "TRY", null, "BIST30 Future");
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "100", "2");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("120")).build()));

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.assetCode()).isEqualTo("XU030F");
        assertThat(response.assetName()).isEqualTo("LONG · XU030F");
        assertThat(response.entryValueTry()).isEqualByComparingTo("2000");
        assertThat(response.marketValueTry()).isEqualByComparingTo("2400");
        assertThat(response.pnlTry()).isEqualByComparingTo("400");
        assertThat(response.pnlPercent()).isEqualByComparingTo("20");
        assertThat(response.currentPriceTry()).isEqualByComparingTo("120");
        assertThat(response.exitDate()).isNull();
        assertThat(response.realizedPnlTry()).isNull();
        assertThat(response.derivative().contractKind()).isEqualTo("FUTURE");
        assertThat(response.derivative().closed()).isFalse();
    }

    @Test
    void shouldBuildShortFutureAsProfit_whenPriceDropsBelowEntry() {
        // A SHORT profits when price drops (100→80): pnl = (100−80)×10×2 = 400 (DIRECTION-AWARE, reported
        // separately). Market value = current notional 80×10×2 = 1600 (mark-to-market, falls as the short
        // profits) — so value − cost (1600 − 2000 = −400) ≠ pnl (+400); the signed pnl is what's correct.
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "10", null, "TRY", null, "BIST30 Future");
        DerivativePosition dp = openPosition(c, DerivativeDirection.SHORT, "100", "2");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("80")).build()));

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.entryValueTry()).isEqualByComparingTo("2000");
        assertThat(response.pnlTry()).isEqualByComparingTo("400");
        assertThat(response.marketValueTry()).isEqualByComparingTo("1600");
    }

    @Test
    void shouldFallbackToContractLastPrice_whenNoCandle() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "150", "TRY", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "100", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.currentPriceTry()).isEqualByComparingTo("150");
        assertThat(response.assetName()).isEqualTo("LONG · XU030F");
    }

    @Test
    void shouldAppendClosedSuffix_whenPositionClosed() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "150", "TRY", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "100", "1");
        dp.setCloseDate(LocalDate.of(2024, 6, 1));
        dp.setClosePrice(new BigDecimal("130"));
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc(anyString(), any(BigDecimal.class))).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.assetName()).isEqualTo("LONG · XU030F · KAPALI");
        assertThat(response.realizedPnlTry()).isEqualByComparingTo("30");
        assertThat(response.exitDate()).isNotNull();
        assertThat(response.exitPrice()).isEqualByComparingTo("130");
        assertThat(response.derivative().closed()).isTrue();
    }

    @Test
    void shouldComputeZeroPnlPercent_whenEntryNotionalIsZero() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "100", "TRY", null, null);
        DerivativePosition dp = DerivativePosition.builder()
                .id(1L).viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(BigDecimal.ZERO)
                .quantityLot(new BigDecimal("1"))
                .build();
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.entryValueTry()).isEqualByComparingTo("0");
        assertThat(response.pnlPercent()).isEqualByComparingTo("0");
    }

    @Test
    void shouldUseDefaultContractSize_whenContractSizeIsNull() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, null, "150", "TRY", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "100", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.entryValueTry()).isEqualByComparingTo("100");
    }

    @Test
    void shouldUseZeroEntryPrice_whenEntryPriceNull() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "100", "TRY", null, null);
        DerivativePosition dp = DerivativePosition.builder()
                .id(1L).viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .quantityLot(new BigDecimal("1"))
                .build();
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.entryPrice()).isEqualByComparingTo("0");
    }

@Test
    void shouldConvertNativeToTry_whenCurrencyIsForeign() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "10", "USD", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "9", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("F_XAUUSD0625", BigDecimal.ZERO))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("10")).build()));
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(new BigDecimal("30"));

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.currentPriceTry()).isEqualByComparingTo("300");
    }

    @Test
    void shouldReturnNullCurrentPriceWhenFxRateMissing_whenCurrencyIsForeign() {
        ViopContract c = contract("F_XAUUSD0625", ViopContractKind.FUTURE, "1", "10", "USD", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "9", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("F_XAUUSD0625", BigDecimal.ZERO)).thenReturn(Optional.empty());
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(null);

        PositionResponse response = formatter.toPositionResponse(dp);

        // Foreign currency + no live FX → null, NOT native-as-TRY. Earlier behaviour persisted
        // raw USD as TRY which under-counted MV by ~30x in summary/allocation.
        assertThat(response.currentPriceTry()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "LONG,OPTION,true,false",
            "SHORT,OPTION,false,true"
    })
    void shouldSetOptionMaxLossOrGain_whenContractKindIsOption(String direction, String kind,
                                                                boolean maxLossSet, boolean maxGainSet) {
        ViopContract c = contract("OPT", ViopContractKind.valueOf(kind), "1", "100", "TRY", "100", null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.valueOf(direction), "100", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("OPT", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        if (maxLossSet) {
            assertThat(response.derivative().maxLossTry()).isNotNull();
            assertThat(response.derivative().maxGainTry()).isNull();
        }
        if (maxGainSet) {
            assertThat(response.derivative().maxGainTry()).isNotNull();
            assertThat(response.derivative().maxLossTry()).isNull();
        }
    }

    @Test
    void shouldNotSetOptionMaxFields_whenKindIsFuture() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "100", "TRY", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "100", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.derivative().maxLossTry()).isNull();
        assertThat(response.derivative().maxGainTry()).isNull();
    }

    @Test
    void shouldKeepNativePrice_whenCurrencyIsBlank() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", "100", "", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "90", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.currentPriceTry()).isEqualByComparingTo("100");
    }

    @Test
    void shouldKeepNativePrice_whenLiveSourceNull() {
        ViopContract c = contract("XU030F", ViopContractKind.FUTURE, "1", null, "TRY", null, null);
        DerivativePosition dp = openPosition(c, DerivativeDirection.LONG, "90", "1");
        lenient().when(viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc("XU030F", BigDecimal.ZERO)).thenReturn(Optional.empty());

        PositionResponse response = formatter.toPositionResponse(dp);

        assertThat(response.currentPriceTry()).isNull();
    }
}
