package com.finance.common.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackedAssetCodeCacheTest {

    private TrackedAssetRepository repository;
    private TrackedAssetCodeCache cache;

    @BeforeEach
    void setUp() {
        repository = mock(TrackedAssetRepository.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getTrackedAsset().setCodeCacheTtlSeconds(60);
        cache = new TrackedAssetCodeCache(repository, appProperties);
    }

    @Test
    void firstCallLoadsFromRepository() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(trackedAsset("btc"), trackedAsset("eth")));

        List<String> codes = cache.get(TrackedAssetType.CRYPTO);

        assertThat(codes).containsExactly("btc", "eth");
        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void secondCallReturnsCachedValueWithoutRepositoryAccess() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(trackedAsset("btc")));

        cache.get(TrackedAssetType.CRYPTO);
        cache.get(TrackedAssetType.CRYPTO);

        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void invalidateForcesReloadOnNextCall() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND))
                .thenReturn(List.of(trackedAsset("AFA")));

        cache.get(TrackedAssetType.FUND);
        cache.invalidate(TrackedAssetType.FUND);
        cache.get(TrackedAssetType.FUND);

        verify(repository, times(2))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND);
    }

    @Test
    void invalidateDoesNotAffectOtherTypes() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(any()))
                .thenReturn(List.of(trackedAsset("code")));

        cache.get(TrackedAssetType.CRYPTO);
        cache.get(TrackedAssetType.STOCK);
        cache.invalidate(TrackedAssetType.CRYPTO);
        cache.get(TrackedAssetType.STOCK);

        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK);
    }

    @Test
    void differentTypesAreCachedIndependently() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(trackedAsset("btc")));
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND))
                .thenReturn(List.of(trackedAsset("AFA")));

        assertThat(cache.get(TrackedAssetType.CRYPTO)).containsExactly("btc");
        assertThat(cache.get(TrackedAssetType.FUND)).containsExactly("AFA");
        assertThat(cache.get(TrackedAssetType.CRYPTO)).containsExactly("btc");

        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND);
    }

    @Test
    void warmCacheIsNotHitRepeatedly() {
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK))
                .thenReturn(List.of(trackedAsset("AAPL")));

        cache.get(TrackedAssetType.STOCK);
        for (int i = 0; i < 100; i++) {
            cache.get(TrackedAssetType.STOCK);
        }

        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK);
        verify(repository, never())
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
        verify(repository, never())
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND);
    }

    private static TrackedAsset trackedAsset(String code) {
        return TrackedAsset.builder().assetCode(code).build();
    }
}
