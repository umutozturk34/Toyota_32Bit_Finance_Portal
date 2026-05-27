package com.finance.market.crypto.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.crypto.mapper.CryptoResponseMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.repository.CryptoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoMarketAssetProviderTest {

    @Mock private CryptoRepository cryptoRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Crypto> cryptoCacheService;
    @Mock private CryptoResponseMapper cryptoResponseMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private CryptoMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CryptoMarketAssetProvider(
                cryptoRepository, cryptoCacheService, cryptoResponseMapper, trackedAssetQueryService);
    }

    @Test
    void should_returnCryptoType_when_getTypeCalled() {
        // Arrange
        // provider built in setUp

        // Act
        MarketType type = provider.getType();

        // Assert
        assertThat(type).isEqualTo(MarketType.CRYPTO);
    }

    @Test
    void should_returnCryptoTrackedAssetType_when_trackedAssetTypeCalled() {
        // Arrange
        // provider built in setUp

        // Act
        TrackedAssetType type = provider.trackedAssetType();

        // Assert
        assertThat(type).isEqualTo(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_returnIdAsCodeField_when_codeFieldCalled() {
        // Arrange
        // provider built in setUp

        // Act
        String code = provider.codeField();

        // Assert
        assertThat(code).isEqualTo("id");
    }

    @Test
    void should_returnExpectedSearchFields_when_searchFieldsCalled() {
        // Arrange
        // provider built in setUp

        // Act
        List<String> fields = provider.searchFields();

        // Assert
        assertThat(fields).containsExactly("id", "name", "symbol");
    }

    @Test
    void should_returnChangePercentColumn_when_changePercentFieldCalled() {
        // Arrange
        // provider built in setUp

        // Act
        String field = provider.changePercentField();

        // Assert
        assertThat(field).isEqualTo("changePercent");
    }

    @Test
    void should_returnCurrentPriceTryColumn_when_priceFieldCalled() {
        // Arrange
        // provider built in setUp

        // Act
        String field = provider.priceField();

        // Assert
        assertThat(field).isEqualTo("currentPriceTry");
    }

    @Test
    void should_mapAllExpectedSortKeys_when_sortFieldsCalled() {
        // Arrange
        // provider built in setUp

        // Act
        Map<String, String> sortFields = provider.sortFields();

        // Assert
        assertThat(sortFields)
                .containsEntry("price", "currentPriceTry")
                .containsEntry("changePercent", "changePercent")
                .containsEntry("name", "name")
                .containsEntry("volume", "totalVolume")
                .containsEntry("marketCap", "marketCap")
                .containsEntry("default", "changePercent");
    }

    @Test
    void should_returnSnapshotFromCacheService_when_getSnapshotByCodeCalled() {
        // Arrange
        Crypto bitcoin = Crypto.builder().id("bitcoin").symbol("BTC")
                .currentPriceTry(new BigDecimal("1000000")).build();
        when(cryptoCacheService.getSnapshot("bitcoin")).thenReturn(bitcoin);

        // Act
        Crypto result = provider.getSnapshotByCode("bitcoin");

        // Assert
        assertThat(result).isSameAs(bitcoin);
    }

    @Test
    void should_returnEmptyMapping_when_mapToResponsesGivenEmptyList() {
        // Arrange
        when(cryptoResponseMapper.toMarketAssetResponses(List.of())).thenReturn(List.of());

        // Act
        List<MarketAssetResponse> result = provider.mapToResponses(List.of());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_delegateMappingToCryptoResponseMapper_when_mapToResponsesGivenEntities() {
        // Arrange
        Crypto bitcoin = Crypto.builder().id("bitcoin").symbol("BTC").build();
        MarketAssetResponse expected = new MarketAssetResponse(
                "bitcoin", "Bitcoin", null, MarketType.CRYPTO,
                new BigDecimal("1000"), null, null, null, null);
        when(cryptoResponseMapper.toMarketAssetResponses(List.of(bitcoin)))
                .thenReturn(List.of(expected));

        // Act
        List<MarketAssetResponse> result = provider.mapToResponses(List.of(bitcoin));

        // Assert
        assertThat(result).containsExactly(expected);
    }
}
