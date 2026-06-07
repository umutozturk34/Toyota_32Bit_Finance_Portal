package com.finance.notification.market;

import com.finance.common.config.AppProperties;
import com.finance.notification.config.MarketSessionProperties;
import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStatusReadTierTest {

    private MarketStatusReadTier tier;

    @BeforeEach
    void setUp() {
        tier = new MarketStatusReadTier(new MarketSessionProperties(7, 120L));
    }

    @Test
    void should_exposeNameAndErrorMetadata_when_queried() {
        assertThat(tier.name()).isEqualTo("MARKET_STATUS_READ");
        assertThat(tier.errorCode()).isEqualTo("RATE_LIMIT_MARKET_STATUS_EXCEEDED");
        assertThat(tier.errorMessage()).isEqualTo("error.rateLimit.marketStatusRead");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/market-status, true",
            "get, /api/v1/market-status/anything, true",
            "POST, /api/v1/market-status, false",
            "GET, /api/v1/notifications, false"
    })
    void should_matchOnlyGetMarketStatusPaths_when_evaluatingRequests(String method, String path, boolean expected) {
        assertThat(tier.matches(path, method)).isEqualTo(expected);
    }

    @Test
    void should_buildBandwidthFromConfiguredCapacity_when_toBandwidthCalled() {
        Bandwidth bandwidth = tier.toBandwidth(new AppProperties.RateLimit());

        assertThat(bandwidth.getCapacity()).isEqualTo(120L);
    }
}
