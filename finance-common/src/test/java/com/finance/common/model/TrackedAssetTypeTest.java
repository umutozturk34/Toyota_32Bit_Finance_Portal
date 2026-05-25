package com.finance.common.model;

import com.finance.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackedAssetTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CRYPTO,    CRYPTO",
            "STOCK,     STOCK",
            "FUND,      FUND",
            "COMMODITY, COMMODITY"
    })
    void marketTypeMapsEachConstantToMatchingMarketType(TrackedAssetType tracked, MarketType expected) {
        MarketType actual = tracked.marketType();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "CRYPTO,    bitcoin,          bitcoin",
            "CRYPTO,    BITCOIN,          bitcoin",
            "CRYPTO,    '  ETH  ',        eth",
            "STOCK,     thyao,            THYAO",
            "STOCK,     '  xu100 ',       XU100",
            "FUND,      afa,              AFA",
            "COMMODITY, gold,             GOLD",
            "COMMODITY, '  xautryg  ',    XAUTRYG"
    })
    void normalizeCodeRespectsPerTypeCasingRules(TrackedAssetType tracked, String raw, String expected) {
        String actual = tracked.normalizeCode(raw);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void normalizeCodeIsLocaleIndependentForTurkishDottedI() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            String crypto = TrackedAssetType.CRYPTO.normalizeCode("BITCOIN");
            String stock = TrackedAssetType.STOCK.normalizeCode("thyao");

            assertThat(crypto).isEqualTo("bitcoin");
            assertThat(stock).isEqualTo("THYAO");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void shouldRaiseBadRequest_whenNormalizeCodeReceivesBlankInput() {
        assertThatThrownBy(() -> TrackedAssetType.STOCK.normalizeCode("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.assetCode.blank");
    }

    @Test
    void shouldRaiseBadRequest_whenNormalizeCodeReceivesNull() {
        assertThatThrownBy(() -> TrackedAssetType.CRYPTO.normalizeCode(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldNormaliseForexAndViop_whenAllUpperRulesApply() {
        assertThat(TrackedAssetType.FOREX.normalizeCode("usdtry")).isEqualTo("USDTRY");
        assertThat(TrackedAssetType.VIOP.normalizeCode("xu030vade")).isEqualTo("XU030VADE");
    }

    @Test
    void shouldResolveBinanceSymbol_whenCryptoRequested() {
        assertThat(TrackedAssetType.CRYPTO.resolveBinanceSymbol("btcusdt"))
                .isEqualTo("BTCUSDT");
        assertThat(TrackedAssetType.CRYPTO.resolveBinanceSymbol("  ethusdt  "))
                .isEqualTo("ETHUSDT");
    }

    @Test
    void shouldReturnNullBinanceSymbol_whenRequestedIsNullOrBlank() {
        assertThat(TrackedAssetType.CRYPTO.resolveBinanceSymbol(null)).isNull();
        assertThat(TrackedAssetType.CRYPTO.resolveBinanceSymbol("   ")).isNull();
    }

    @Test
    void shouldReturnNullBinanceSymbol_whenTypeNotCrypto() {
        assertThat(TrackedAssetType.STOCK.resolveBinanceSymbol("AKBNK.IS")).isNull();
        assertThat(TrackedAssetType.FUND.resolveBinanceSymbol("AFA")).isNull();
    }

    @Test
    void shouldPreferRequestedSegment_whenStockResolvingSegment() {
        StockSegment resolved = TrackedAssetType.STOCK.resolveSegment(
                StockSegment.MAIN_INDEX, StockSegment.EQUITY);

        assertThat(resolved).isEqualTo(StockSegment.MAIN_INDEX);
    }

    @Test
    void shouldFallbackToExistingSegment_whenRequestedNull() {
        StockSegment resolved = TrackedAssetType.STOCK.resolveSegment(null, StockSegment.MAIN_INDEX);

        assertThat(resolved).isEqualTo(StockSegment.MAIN_INDEX);
    }

    @Test
    void shouldDefaultToEquity_whenStockSegmentMissing() {
        StockSegment resolved = TrackedAssetType.STOCK.resolveSegment(null, null);

        assertThat(resolved).isEqualTo(StockSegment.EQUITY);
    }

    @Test
    void shouldReturnNullSegment_whenTypeIsNotStock() {
        assertThat(TrackedAssetType.CRYPTO.resolveSegment(StockSegment.MAIN_INDEX, StockSegment.EQUITY))
                .isNull();
    }

    @Test
    void shouldHonorRequestedIndexFlag_whenProvided() {
        boolean result = TrackedAssetType.STOCK.resolveIndexAsset(
                StockSegment.MAIN_INDEX, Boolean.FALSE, true);

        assertThat(result).isFalse();
    }

    @Test
    void shouldInferIndexFromNonEquitySegment_whenRequestedNull() {
        boolean result = TrackedAssetType.STOCK.resolveIndexAsset(
                StockSegment.MAIN_INDEX, null, false);

        assertThat(result).isTrue();
    }

    @Test
    void shouldFallbackToExistingIndexFlag_whenSegmentIsEquity() {
        boolean result = TrackedAssetType.STOCK.resolveIndexAsset(StockSegment.EQUITY, null, true);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseForResolveIndexAsset_whenTypeNotStock() {
        assertThat(TrackedAssetType.CRYPTO.resolveIndexAsset(StockSegment.MAIN_INDEX, null, false))
                .isFalse();
    }

    @Test
    void shouldHonorRequestedCompareOnly_whenProvided() {
        boolean result = TrackedAssetType.STOCK.resolveCompareOnly(
                StockSegment.SECONDARY_INDEX, Boolean.FALSE, true);

        assertThat(result).isFalse();
    }

    @Test
    void shouldFlagCompareOnly_whenSegmentSecondaryIndex() {
        boolean result = TrackedAssetType.STOCK.resolveCompareOnly(
                StockSegment.SECONDARY_INDEX, null, false);

        assertThat(result).isTrue();
    }

    @Test
    void shouldFallbackToExistingCompareOnly_whenSegmentNotSecondary() {
        boolean result = TrackedAssetType.STOCK.resolveCompareOnly(StockSegment.EQUITY, null, true);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseCompareOnly_whenTypeNotStock() {
        assertThat(TrackedAssetType.FUND.resolveCompareOnly(StockSegment.SECONDARY_INDEX, null, false))
                .isFalse();
    }
}
