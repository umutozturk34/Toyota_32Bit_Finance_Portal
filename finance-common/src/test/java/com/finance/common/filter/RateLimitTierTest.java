package com.finance.common.filter;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.tier.AdminReadTier;
import com.finance.common.filter.tier.AdminTriggerTier;
import com.finance.common.filter.tier.ApiTier;
import com.finance.common.filter.tier.CredentialActionTier;
import io.github.bucket4j.Bandwidth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTierTest {

    private AppProperties.RateLimit rateLimit;
    private List<RateLimitTier> tiers;

    @BeforeEach
    void setUp() {
        rateLimit = new AppProperties.RateLimit();
        rateLimit.setAdminTriggerLimit(5);
        rateLimit.setAdminReadLimit(60);
        rateLimit.setApiLimit(600);
        rateLimit.setCredentialActionLimit(7);
        tiers = List.of(new AdminTriggerTier(), new AdminReadTier(), new CredentialActionTier(), new ApiTier());
    }

    @Test
    void shouldRouteAdminTriggerPost_whenPathStartsWithAdminTrigger() {
        RateLimitTier matched = firstMatch("/api/v1/admin/trigger/crypto/refresh", "POST");

        assertThat(matched.name()).isEqualTo("ADMIN_TRIGGER");
    }

    @Test
    void shouldFallbackToAdminRead_whenAdminTriggerNotPost() {
        RateLimitTier matched = firstMatch("/api/v1/admin/trigger/crypto/refresh", "GET");

        assertThat(matched.name()).isEqualTo("ADMIN_READ");
    }

    @Test
    void shouldRouteAdminRead_whenPathStartsWithAdmin() {
        RateLimitTier matched = firstMatch("/api/v1/admin/users", "GET");

        assertThat(matched.name()).isEqualTo("ADMIN_READ");
    }

    @Test
    void shouldRouteCredentialAction_whenPathStartsWithUserCredentials() {
        RateLimitTier matched = firstMatch("/api/v1/user/credentials/password/initiate-change", "POST");

        assertThat(matched.name()).isEqualTo("CREDENTIAL_ACTION");
    }

    @Test
    void shouldFallbackToApi_whenNoSpecificMatch() {
        RateLimitTier matched = firstMatch("/api/v1/portfolio", "GET");

        assertThat(matched.name()).isEqualTo("API");
    }

    @Test
    void shouldExposeUniqueErrorCodes() {
        assertThat(tiers.stream().map(RateLimitTier::errorCode).distinct().count())
                .isEqualTo(tiers.size());
    }

    @Test
    void shouldNonBlankErrorMessageForEveryTier() {
        tiers.forEach(t -> assertThat(t.errorMessage()).isNotBlank());
    }

    @Test
    void shouldReadCapacityFromConfiguredLimits() {
        assertCapacity("ADMIN_TRIGGER", 5);
        assertCapacity("ADMIN_READ", 60);
        assertCapacity("API", 600);
        assertCapacity("CREDENTIAL_ACTION", 7);
    }

    @Test
    void shouldReflectUpdatedLimits_whenToBandwidthCalledLater() {
        rateLimit.setApiLimit(999);
        rateLimit.setCredentialActionLimit(999);

        assertCapacity("API", 999);
        assertCapacity("CREDENTIAL_ACTION", 999);
    }

    private RateLimitTier firstMatch(String path, String method) {
        return tiers.stream()
                .filter(t -> t.matches(path, method))
                .findFirst()
                .orElseThrow();
    }

    private void assertCapacity(String tierName, long expected) {
        Bandwidth bw = tiers.stream()
                .filter(t -> t.name().equals(tierName))
                .findFirst()
                .orElseThrow()
                .toBandwidth(rateLimit);
        assertThat(bw.getCapacity()).isEqualTo(expected);
    }
}
