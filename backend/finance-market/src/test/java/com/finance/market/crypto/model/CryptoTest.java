package com.finance.market.crypto.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoTest {

    private Crypto crypto() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");
        crypto.setSymbol("BTC");
        crypto.setCurrentPrice(new BigDecimal("60000.123456"));
        crypto.setCurrentPriceTry(new BigDecimal("2000000.987654"));
        crypto.setMarketCap(new BigDecimal("1200000000.555555"));
        crypto.setTotalVolume(new BigDecimal("30000000.444444"));
        crypto.setChangeAmount(new BigDecimal("100.111111"));
        crypto.setChangePercent(new BigDecimal("1.999999"));
        return crypto;
    }

    @Test
    void should_scaleAllMonetaryFields_when_scaleFieldsApplied() {
        Crypto crypto = crypto();

        crypto.scaleFields(2);

        assertThat(crypto.getCurrentPrice()).isEqualByComparingTo("60000.12");
        assertThat(crypto.getCurrentPriceTry()).isEqualByComparingTo("2000000.99");
        assertThat(crypto.getMarketCap()).isEqualByComparingTo("1200000000.56");
        assertThat(crypto.getTotalVolume()).isEqualByComparingTo("30000000.44");
        assertThat(crypto.getChangeAmount()).isEqualByComparingTo("100.11");
        assertThat(crypto.getChangePercent()).isEqualByComparingTo("2.00");
    }

    @Test
    void should_leaveNullMonetaryFieldsNull_when_scaleFieldsApplied() {
        Crypto crypto = new Crypto();
        crypto.setId("ethereum");

        crypto.scaleFields(4);

        assertThat(crypto.getCurrentPrice()).isNull();
        assertThat(crypto.getCurrentPriceTry()).isNull();
        assertThat(crypto.getMarketCap()).isNull();
        assertThat(crypto.getTotalVolume()).isNull();
    }

    @Test
    void should_exposeIdAsCode_when_getCodeCalled() {
        Crypto crypto = crypto();

        assertThat(crypto.getCode()).isEqualTo("bitcoin");
    }

    @Test
    void should_returnTryPrice_when_getPriceTryCalled() {
        Crypto crypto = crypto();

        assertThat(crypto.getPriceTry()).isEqualByComparingTo("2000000.987654");
    }

    @Test
    void should_preferName_when_nameIsPresent() {
        Crypto crypto = crypto();
        crypto.setName("Bitcoin");

        assertThat(crypto.resolveDisplayName()).isEqualTo("Bitcoin");
    }

    @Test
    void should_fallBackToSymbol_when_nameIsBlank() {
        Crypto crypto = crypto();
        crypto.setName(" ");

        assertThat(crypto.resolveDisplayName()).isEqualTo("BTC");
    }

    @Test
    void should_fallBackToId_when_nameAndSymbolBlank() {
        Crypto crypto = new Crypto();
        crypto.setId("bitcoin");

        assertThat(crypto.resolveDisplayName()).isEqualTo("bitcoin");
    }
}
