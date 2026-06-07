package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.response.AssetReturnRow;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AssetReturnsCacheManagerTest {

    private final AssetReturnsCacheManager cache = new AssetReturnsCacheManager();

    private static AssetReturnsResponse nonEmpty() {
        return new AssetReturnsResponse(LocalDate.now(),
                List.of(new AssetReturnRow(AnalyticsInstrumentType.SPOT, "AAA", "AAA", Map.of())));
    }

    private static AssetReturnsResponse empty() {
        return new AssetReturnsResponse(LocalDate.now(), List.of());
    }

    @Test
    void shouldComputeOnceThenServeFromCache_whenResultIsNonEmpty() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        Supplier<AssetReturnsResponse> loader = () -> {
            calls.incrementAndGet();
            return nonEmpty();
        };

        // Act
        AssetReturnsResponse first = cache.getOrCompute(loader);
        AssetReturnsResponse second = cache.getOrCompute(loader);

        // Assert
        assertThat(calls.get()).isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void shouldNotRetainEmptyResult_soItRecomputesEachCall() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        Supplier<AssetReturnsResponse> loader = () -> {
            calls.incrementAndGet();
            return empty();
        };

        // Act
        cache.getOrCompute(loader);
        cache.getOrCompute(loader);

        // Assert — empty (cold-start) results are never cached, so the loader runs every time.
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void shouldStoreRefreshedResult_servedByLaterGet() {
        // Arrange
        AtomicInteger getCalls = new AtomicInteger();

        // Act
        cache.refresh(AssetReturnsCacheManagerTest::nonEmpty);
        cache.getOrCompute(() -> {
            getCalls.incrementAndGet();
            return nonEmpty();
        });

        // Assert — refresh populated the cache, so getOrCompute never invoked its loader.
        assertThat(getCalls.get()).isZero();
    }

    @Test
    void shouldNotStoreEmptyRefresh() {
        // Arrange
        AtomicInteger getCalls = new AtomicInteger();

        // Act
        cache.refresh(AssetReturnsCacheManagerTest::empty);
        cache.getOrCompute(() -> {
            getCalls.incrementAndGet();
            return nonEmpty();
        });

        // Assert — an empty refresh isn't cached, so the next get has to compute.
        assertThat(getCalls.get()).isEqualTo(1);
    }

    @Test
    void shouldEvictOnClear() {
        // Arrange
        cache.refresh(AssetReturnsCacheManagerTest::nonEmpty);

        // Act
        cache.clear();
        AtomicInteger getCalls = new AtomicInteger();
        cache.getOrCompute(() -> {
            getCalls.incrementAndGet();
            return nonEmpty();
        });

        // Assert — after clear the cache is cold, so the loader runs again.
        assertThat(getCalls.get()).isEqualTo(1);
    }

    @Test
    void shouldClassifyWorthCaching() {
        // Assert
        assertThat(cache.isWorthCaching(nonEmpty())).isTrue();
        assertThat(cache.isWorthCaching(empty())).isFalse();
        assertThat(cache.isWorthCaching(null)).isFalse();
        assertThat(cache.isWorthCaching(new AssetReturnsResponse(LocalDate.now(), null))).isFalse();
    }
}
