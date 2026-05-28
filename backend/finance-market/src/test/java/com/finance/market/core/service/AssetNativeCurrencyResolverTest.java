package com.finance.market.core.service;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.currency.CommodityNativeCurrencyStrategy;
import com.finance.market.core.service.currency.DepositNativeCurrencyStrategy;
import com.finance.market.core.service.currency.ForexNativeCurrencyStrategy;
import com.finance.market.core.service.currency.MacroIndicatorNativeCurrencyStrategy;
import com.finance.market.core.service.currency.TryNativeCurrencyStrategy;
import com.finance.market.core.service.currency.UsdNativeCurrencyStrategy;
import com.finance.market.core.service.currency.ViopNativeCurrencyStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetNativeCurrencyResolverTest {

    private final AssetNativeCurrencyResolver resolver = new AssetNativeCurrencyResolver.Default(List.of(
            new TryNativeCurrencyStrategy(),
            new UsdNativeCurrencyStrategy(),
            new CommodityNativeCurrencyStrategy(),
            new ForexNativeCurrencyStrategy(),
            new ViopNativeCurrencyStrategy(),
            new DepositNativeCurrencyStrategy(),
            new MacroIndicatorNativeCurrencyStrategy()
    ));

    @ParameterizedTest
    @CsvSource({
            "STOCK,    THYAO.IS, TRY",
            "FUND,     TEST,     TRY",
            "BOND,     T_2030,   TRY",
            "CRYPTO,   bitcoin,  USD"
    })
    void resolveStaticTypesToFixedCurrency(MarketType type, String code, Currency expected) {
        assertThat(resolver.resolveNativeCurrency(type, code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "XAUTRY", "XAGTRY", "XAUUSD", "XAU", "XAGEUR" })
    void resolveCommodityAlwaysReturnsTryBecauseStoredInTry(String code) {
        assertThat(resolver.resolveNativeCurrency(MarketType.COMMODITY, code)).isEqualTo(Currency.TRY);
    }

    @Test
    void resolveCommodityReturnsTryForBlankCode() {
        assertThat(resolver.resolveNativeCurrency(MarketType.COMMODITY, "")).isEqualTo(Currency.TRY);
        assertThat(resolver.resolveNativeCurrency(MarketType.COMMODITY, null)).isEqualTo(Currency.TRY);
    }

    @ParameterizedTest
    @CsvSource({
            "USD",
            "EUR",
            "TRY",
            "GBP",
            "''"
    })
    void resolveForexAlwaysReturnsTryBecausePriceIsTryQuoted(String code) {
        assertThat(resolver.resolveNativeCurrency(MarketType.FOREX, code)).isEqualTo(Currency.TRY);
    }

    @ParameterizedTest
    @CsvSource({
            "TP.TRYTAS.MT06, TRY",
            "TP.USDTAS.MT12, USD",
            "TP.EURTAS.MT12, EUR"
    })
    void resolveDepositReadsCurrencyFromCodePrefix(String code, Currency expected) {
        assertThat(resolver.resolveNativeCurrency(MarketType.MACRO_DEPOSIT, code)).isEqualTo(expected);
    }

    @Test
    void resolveDepositFallsBackToTryWhenCodeUnparseable() {
        assertThat(resolver.resolveNativeCurrency(MarketType.MACRO_DEPOSIT, "WEIRD"))
                .isEqualTo(Currency.TRY);
        assertThat(resolver.resolveNativeCurrency(MarketType.MACRO_DEPOSIT, "TP.XYZTAS"))
                .isEqualTo(Currency.TRY);
    }

    @Test
    void resolveMacroIndicatorsReturnsTry() {
        assertThat(resolver.resolveNativeCurrency(MarketType.MACRO_INFLATION, "TP.TUFE")).isEqualTo(Currency.TRY);
        assertThat(resolver.resolveNativeCurrency(MarketType.MACRO_RATE, "TP.POLICY")).isEqualTo(Currency.TRY);
    }

    @Test
    void resolveNullTypeReturnsTry() {
        assertThat(resolver.resolveNativeCurrency(null, "anything")).isEqualTo(Currency.TRY);
    }

    @ParameterizedTest
    @CsvSource({
            "F_USDTRY0625,        TRY",
            "F_EURTRY0625,        TRY",
            "F_ASELS0625,         TRY",
            "F_EURUSD0625,        USD",
            "F_XAUEUR0625,        EUR",
            "O_AKBNKE0526P45.00,  TRY"
    })
    void resolveViopReadsQuoteCurrencyFromSymbolSuffix(String code, Currency expected) {
        assertThat(resolver.resolveNativeCurrency(MarketType.VIOP, code)).isEqualTo(expected);
    }

    @Test
    void resolveViopFallsBackToTryForBlankCode() {
        assertThat(resolver.resolveNativeCurrency(MarketType.VIOP, "")).isEqualTo(Currency.TRY);
        assertThat(resolver.resolveNativeCurrency(MarketType.VIOP, null)).isEqualTo(Currency.TRY);
    }
}
