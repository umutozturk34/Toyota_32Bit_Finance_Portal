package com.finance.common.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentTest {

    @Test
    void create_buildsActiveAssetWithMarketTypeAndCode() {
        Instrument asset = Instrument.create(MarketType.CRYPTO, "bitcoin");

        assertThat(asset.getMarketType()).isEqualTo(MarketType.CRYPTO);
        assertThat(asset.getAssetCode()).isEqualTo("bitcoin");
        assertThat(asset.isActive()).isTrue();
    }

    @Test
    void matches_returnsTrue_whenMarketTypeAndCodeMatchIgnoreCase() {
        Instrument asset = Instrument.create(MarketType.STOCK, "THYAO");

        assertThat(asset.matches(MarketType.STOCK, "thyao")).isTrue();
    }

    @Test
    void matches_returnsFalse_whenMarketTypeDiffers() {
        Instrument asset = Instrument.create(MarketType.STOCK, "THYAO");

        assertThat(asset.matches(MarketType.CRYPTO, "thyao")).isFalse();
    }

    @Test
    void deactivate_andReactivate_togglesActiveFlag() {
        Instrument asset = Instrument.create(MarketType.STOCK, "THYAO");

        asset.deactivate();
        assertThat(asset.isActive()).isFalse();

        asset.reactivate();
        assertThat(asset.isActive()).isTrue();
    }

    @Test
    void prePersist_assignsTimestamps_whenNull() throws Exception {
        Instrument asset = Instrument.create(MarketType.STOCK, "THYAO");
        invokePackagePrivate(asset, "prePersist");

        assertThat(asset.getCreatedAt()).isNotNull();
        assertThat(asset.getUpdatedAt()).isNotNull();
    }

    private void invokePackagePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }
}
