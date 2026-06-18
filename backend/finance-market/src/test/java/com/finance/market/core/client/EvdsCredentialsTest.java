package com.finance.market.core.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsCredentialsTest {

    @Test
    void should_reportConfigured_when_apiKeyIsPresent() {
        // Arrange + Act
        EvdsCredentials credentials = new EvdsCredentials("a-real-evds-key");

        // Assert
        assertThat(credentials.isConfigured()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void should_reportNotConfigured_when_apiKeyIsNullOrBlank(String apiKey) {
        // Arrange + Act — an unset env var resolves to "" via @Value's default, and whitespace is not a real key.
        EvdsCredentials credentials = new EvdsCredentials(apiKey);

        // Assert
        assertThat(credentials.isConfigured()).isFalse();
    }
}
