package com.finance.backend.filter;

import com.finance.backend.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TierTest {

    private AppProperties.RateLimit rateLimit;

    @BeforeEach
    void setUp() {
        rateLimit = new AppProperties.RateLimit();
        rateLimit.setAdminTriggerLimit(5);
        rateLimit.setAdminReadLimit(60);
        rateLimit.setApiLimit(600);
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN_TRIGGER, RATE_LIMIT_ADMIN_TRIGGER_EXCEEDED",
            "ADMIN_READ,    RATE_LIMIT_ADMIN_READ_EXCEEDED",
            "API,           RATE_LIMIT_API_EXCEEDED"
    })
    void errorCodeMatchesEachTier(Tier tier, String expectedCode) {
        assertThat(tier.errorCode()).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void errorMessageIsNonBlankForEveryTier(Tier tier) {
        assertThat(tier.errorMessage()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN_TRIGGER, 5",
            "ADMIN_READ,    60",
            "API,           600"
    })
    void toBandwidthUsesConfiguredCapacityPerTier(Tier tier, long expectedCapacity) {
        Bandwidth bandwidth = tier.toBandwidth(rateLimit);

        assertThat(bandwidth.getCapacity()).isEqualTo(expectedCapacity);
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void toBandwidthPicksUpUpdatedPropertiesAtCallTime(Tier tier) {
        rateLimit.setAdminTriggerLimit(999);
        rateLimit.setAdminReadLimit(999);
        rateLimit.setApiLimit(999);

        Bandwidth bandwidth = tier.toBandwidth(rateLimit);

        assertThat(bandwidth.getCapacity()).isEqualTo(999);
    }
}
