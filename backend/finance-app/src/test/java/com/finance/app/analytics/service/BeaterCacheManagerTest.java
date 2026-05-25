package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.response.InflationBeaterEntry;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.common.model.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class BeaterCacheManagerTest {

    private BeaterCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new BeaterCacheManager();
    }

    private InflationBeaterResponse nonEmptyResponse() {
        InflationBeaterEntry entry = new InflationBeaterEntry(
                AnalyticsInstrumentType.SPOT, "A", "Name A",
                new BigDecimal("10"), new BigDecimal("5"), true);
        return new InflationBeaterResponse(LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.CPI", "cpi", new BigDecimal("5"), 1, 1, Currency.TRY, List.of(entry));
    }

    private InflationBeaterResponse emptyResponse() {
        return new InflationBeaterResponse(LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.CPI", "cpi", BigDecimal.ZERO, 0, 0, Currency.TRY, List.of());
    }

    private InflationBeaterResponse nullEntriesResponse() {
        return new InflationBeaterResponse(LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.CPI", "cpi", BigDecimal.ZERO, 0, 0, Currency.TRY, null);
    }

    @Test
    void should_returnFromLoaderAndCache_when_keyMissing() {
        InflationBeaterResponse expected = nonEmptyResponse();
        AtomicInteger calls = new AtomicInteger();
        Supplier<InflationBeaterResponse> loader = () -> {
            calls.incrementAndGet();
            return expected;
        };

        InflationBeaterResponse first = cacheManager.getOrCompute("key1", loader);
        InflationBeaterResponse second = cacheManager.getOrCompute("key1", loader);

        assertThat(first).isSameAs(expected);
        assertThat(second).isSameAs(expected);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void should_invalidateCachedEntry_when_loaderReturnsEmptyResult() {
        InflationBeaterResponse empty = emptyResponse();

        InflationBeaterResponse result = cacheManager.getOrCompute("emptyKey", () -> empty);

        assertThat(result).isSameAs(empty);
        assertThat(cacheManager.peek("emptyKey")).isNull();
    }

    @Test
    void should_returnNullAndNotCache_when_loaderReturnsNull() {
        InflationBeaterResponse result = cacheManager.getOrCompute("nullKey", () -> null);

        assertThat(result).isNull();
        assertThat(cacheManager.peek("nullKey")).isNull();
    }

    @Test
    void should_returnPresentEntry_when_peekFindsCachedValue() {
        InflationBeaterResponse expected = nonEmptyResponse();
        cacheManager.getOrCompute("peek-key", () -> expected);

        assertThat(cacheManager.peek("peek-key")).isSameAs(expected);
    }

    @Test
    void should_returnNull_when_peekMissesKey() {
        assertThat(cacheManager.peek("ghost")).isNull();
    }

    @Test
    void should_storeWarmedResult_when_warmAsyncProducesNonEmptyResponse() {
        InflationBeaterResponse expected = nonEmptyResponse();

        cacheManager.warmAsync("warm-key", "1Y", "TP.CPI", () -> expected);

        assertThat(cacheManager.peek("warm-key")).isSameAs(expected);
    }

    @Test
    void should_skipCaching_when_warmAsyncProducesEmptyResponse() {
        cacheManager.warmAsync("warm-empty", "1Y", "TP.CPI", this::emptyResponse);

        assertThat(cacheManager.peek("warm-empty")).isNull();
    }

    @Test
    void should_skipLoader_when_warmAsyncKeyAlreadyCached() {
        InflationBeaterResponse cached = nonEmptyResponse();
        cacheManager.getOrCompute("dup-key", () -> cached);
        AtomicInteger calls = new AtomicInteger();

        cacheManager.warmAsync("dup-key", "1Y", "TP.CPI", () -> {
            calls.incrementAndGet();
            return nonEmptyResponse();
        });

        assertThat(calls.get()).isZero();
        assertThat(cacheManager.peek("dup-key")).isSameAs(cached);
    }

    @Test
    void should_swallowLoaderException_when_warmAsyncLoaderThrows() {
        cacheManager.warmAsync("bad-key", "1Y", "TP.CPI", () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(cacheManager.peek("bad-key")).isNull();
    }

    @Test
    void should_overwriteCache_when_refreshProducesNewNonEmptyResult() {
        InflationBeaterResponse first = nonEmptyResponse();
        cacheManager.getOrCompute("refresh-key", () -> first);
        InflationBeaterResponse second = nonEmptyResponse();

        cacheManager.refresh("refresh-key", "1Y", "TP.CPI", () -> second);

        assertThat(cacheManager.peek("refresh-key")).isSameAs(second);
    }

    @Test
    void should_keepExistingCache_when_refreshProducesEmptyResult() {
        InflationBeaterResponse existing = nonEmptyResponse();
        cacheManager.getOrCompute("keep-key", () -> existing);

        cacheManager.refresh("keep-key", "1Y", "TP.CPI", this::emptyResponse);

        assertThat(cacheManager.peek("keep-key")).isSameAs(existing);
    }

    @Test
    void should_propagateException_when_refreshLoaderThrows() {
        assertThatThrownBy(() -> cacheManager.refresh("err-key", "1Y", "TP.CPI", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    @Test
    void should_evictAllEntries_when_clearIsCalled() {
        cacheManager.getOrCompute("a", this::nonEmptyResponse);
        cacheManager.getOrCompute("b", this::nonEmptyResponse);

        cacheManager.clear();

        assertThat(cacheManager.peek("a")).isNull();
        assertThat(cacheManager.peek("b")).isNull();
    }

    @Test
    void should_concatenatePeriodCodeOverride_when_buildKeyHasOverride() {
        String key = cacheManager.buildKey("1Y", "TP.CPI", Currency.USD);

        assertThat(key).isEqualTo("1Y|TP.CPI|USD");
    }

    @Test
    void should_useAutoSentinel_when_buildKeyOverrideIsNull() {
        String key = cacheManager.buildKey("6M", "TP.CPI", null);

        assertThat(key).isEqualTo("6M|TP.CPI|AUTO");
    }

    @Test
    void should_returnTrue_when_responseHasEntries() {
        boolean worth = cacheManager.isWorthCaching(nonEmptyResponse());

        assertThat(worth).isTrue();
    }

    @Test
    void should_returnFalse_when_responseIsNull() {
        boolean worth = cacheManager.isWorthCaching(null);

        assertThat(worth).isFalse();
    }

    @Test
    void should_returnFalse_when_responseEntriesAreNull() {
        boolean worth = cacheManager.isWorthCaching(nullEntriesResponse());

        assertThat(worth).isFalse();
    }

    @Test
    void should_returnFalse_when_responseEntriesAreEmpty() {
        boolean worth = cacheManager.isWorthCaching(emptyResponse());

        assertThat(worth).isFalse();
    }

    @Test
    void should_allowReWarmAfterClear_when_inFlightSlotReleased() {
        cacheManager.warmAsync("recycle", "1Y", "TP.CPI", this::nonEmptyResponse);
        cacheManager.clear();
        InflationBeaterResponse fresh = nonEmptyResponse();

        cacheManager.warmAsync("recycle", "1Y", "TP.CPI", () -> fresh);

        assertThat(cacheManager.peek("recycle")).isSameAs(fresh);
    }
}
