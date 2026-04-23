package com.finance.backend.util;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TrackedRefreshRunnerTest {

    private Logger log;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void refreshSnapshotShortCircuitsOnBlank(String code) {
        AtomicBoolean called = new AtomicBoolean();

        TrackedRefreshRunner.refreshSnapshot(code, CodeNormalizer::upper,
                normalized -> {
                    called.set(true);
                    return true;
                },
                log, "stock");

        assertThat(called).isFalse();
        verify(log, never()).info(anyString(), anyString(), anyString());
    }

    @Test
    void refreshSnapshotRunsAndLogsOnSuccess() {
        AtomicReference<String> captured = new AtomicReference<>();

        TrackedRefreshRunner.refreshSnapshot("aapl", CodeNormalizer::upper,
                normalized -> {
                    captured.set(normalized);
                    return true;
                },
                log, "stock");

        assertThat(captured.get()).isEqualTo("AAPL");
        verify(log, times(1)).info("Refreshed tracked {} snapshot for {}", "stock", "AAPL");
    }

    @Test
    void refreshSnapshotRunsButSkipsLogOnShortCircuit() {
        AtomicReference<String> captured = new AtomicReference<>();

        TrackedRefreshRunner.refreshSnapshot("bitcoin", CodeNormalizer::lower,
                normalized -> {
                    captured.set(normalized);
                    return false;
                },
                log, "crypto");

        assertThat(captured.get()).isEqualTo("bitcoin");
        verify(log, never()).info(anyString(), anyString(), anyString());
    }

    @Test
    void refreshSnapshotLowerCasesInput() {
        AtomicReference<String> captured = new AtomicReference<>();

        TrackedRefreshRunner.refreshSnapshot("BTC", CodeNormalizer::lower,
                normalized -> {
                    captured.set(normalized);
                    return true;
                },
                log, "crypto");

        assertThat(captured.get()).isEqualTo("btc");
        verify(log, times(1)).info("Refreshed tracked {} snapshot for {}", "crypto", "btc");
    }

    @Test
    void refreshSnapshotSkipsWhenNormalizerReturnsBlank() {
        AtomicBoolean called = new AtomicBoolean();

        TrackedRefreshRunner.refreshSnapshot("whatever", input -> "",
                normalized -> {
                    called.set(true);
                    return true;
                },
                log, "fund");

        assertThat(called).isFalse();
        verify(log, never()).info(anyString(), anyString(), anyString());
    }
}
