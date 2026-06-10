package com.finance.market.core.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedAssetCodeCacheTest {

    @Mock private TrackedAssetRepository repository;

    private TrackedAssetCodeCache cache;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getTrackedAsset().setCodeCacheTtlSeconds(60);
        cache = new TrackedAssetCodeCache(repository, props);
    }

    private TrackedAsset asset(String code) {
        TrackedAsset a = new TrackedAsset();
        a.setAssetType(TrackedAssetType.CRYPTO);
        a.setAssetCode(code);
        return a;
    }

    private TrackedAsset named(String code, String displayName) {
        TrackedAsset a = asset(code);
        a.setDisplayName(displayName);
        return a;
    }

    @Test
    void should_loadCodesFromRepository_when_getCalledFirstTime() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(asset("bitcoin"), asset("ethereum")));

        // Act
        List<String> result = cache.get(TrackedAssetType.CRYPTO);

        // Assert
        assertThat(result).containsExactly("bitcoin", "ethereum");
        verify(repository, times(1))
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_useCachedValue_when_getCalledTwiceInARow() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(asset("bitcoin")));

        // Act
        cache.get(TrackedAssetType.CRYPTO);
        List<String> second = cache.get(TrackedAssetType.CRYPTO);

        // Assert
        assertThat(second).containsExactly("bitcoin");
        verify(repository, times(1))
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_loadEnabledCodesFromRepository_when_getEnabledCalledFirstTime() {
        // Arrange
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK))
                .thenReturn(List.of(asset("THYAO")));

        // Act
        List<String> result = cache.getEnabled(TrackedAssetType.STOCK);

        // Assert
        assertThat(result).containsExactly("THYAO");
        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK);
    }

    @Test
    void should_useCachedEnabledValue_when_getEnabledCalledTwice() {
        // Arrange
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK))
                .thenReturn(List.of(asset("ASELS")));

        // Act
        cache.getEnabled(TrackedAssetType.STOCK);
        List<String> second = cache.getEnabled(TrackedAssetType.STOCK);

        // Assert
        assertThat(second).containsExactly("ASELS");
        verify(repository, times(1))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.STOCK);
    }

    @Test
    void should_reloadFromRepository_when_invalidatedThenFetched() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(asset("bitcoin")));
        when(repository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(asset("bitcoin")));

        // Act
        cache.get(TrackedAssetType.CRYPTO);
        cache.getEnabled(TrackedAssetType.CRYPTO);
        cache.invalidate(TrackedAssetType.CRYPTO);
        cache.get(TrackedAssetType.CRYPTO);
        cache.getEnabled(TrackedAssetType.CRYPTO);

        // Assert
        verify(repository, times(2))
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
        verify(repository, times(2))
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_returnEmptyList_when_repositoryReturnsEmpty() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND))
                .thenReturn(List.of());

        // Act
        List<String> result = cache.get(TrackedAssetType.FUND);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_loadDisplayNames_filteringBlankAndNull_when_getDisplayNamesCalled() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(named("bitcoin", "Bitcoin"), named("ethereum", "  "), named("ripple", null)));

        // Act
        var result = cache.getDisplayNames(TrackedAssetType.CRYPTO);

        // Assert
        assertThat(result).containsOnlyKeys("bitcoin");
        assertThat(result).containsEntry("bitcoin", "Bitcoin");
    }

    @Test
    void should_useCachedDisplayNames_when_getDisplayNamesCalledTwice() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(named("bitcoin", "Bitcoin")));

        // Act
        cache.getDisplayNames(TrackedAssetType.CRYPTO);
        var second = cache.getDisplayNames(TrackedAssetType.CRYPTO);

        // Assert
        assertThat(second).containsEntry("bitcoin", "Bitcoin");
        verify(repository, times(1))
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_reloadDisplayNames_when_invalidatedThenFetched() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(named("bitcoin", "Bitcoin")));

        // Act
        cache.getDisplayNames(TrackedAssetType.CRYPTO);
        cache.invalidate(TrackedAssetType.CRYPTO);
        cache.getDisplayNames(TrackedAssetType.CRYPTO);

        // Assert
        verify(repository, times(2))
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_returnIndependentCacheValuesPerType_when_differentTypesQueried() {
        // Arrange
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(asset("bitcoin")));
        when(repository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.FUND))
                .thenReturn(List.of(asset("AAL")));

        // Act
        List<String> crypto = cache.get(TrackedAssetType.CRYPTO);
        List<String> fund = cache.get(TrackedAssetType.FUND);

        // Assert
        assertThat(crypto).containsExactly("bitcoin");
        assertThat(fund).containsExactly("AAL");
    }
}
