package com.finance.market.core.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.core.mapper.TrackedAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedAssetQueryServiceTest {

    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private TrackedAssetMapper trackedAssetMapper;
    @Mock private TrackedAssetCodeCache codeCache;

    private TrackedAssetQueryService service;

    @BeforeEach
    void setUp() {
        service = new TrackedAssetQueryService(trackedAssetRepository, trackedAssetMapper, codeCache);
    }

    private TrackedAsset asset(String code, String displayName, String binanceSymbol) {
        TrackedAsset a = new TrackedAsset();
        a.setAssetType(TrackedAssetType.CRYPTO);
        a.setAssetCode(code);
        a.setDisplayName(displayName);
        a.setBinanceSymbol(binanceSymbol);
        return a;
    }

    private TrackedAssetResponse response(String code) {
        return TrackedAssetResponse.builder()
                .assetType(TrackedAssetType.CRYPTO)
                .assetCode(code)
                .displayName(code)
                .build();
    }

    @Test
    void getTrackedAssets_returnsMappedListForType() {
        TrackedAsset a = asset("bitcoin", "Bitcoin", "BTCUSDT");
        when(trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(TrackedAssetType.CRYPTO))
                .thenReturn(List.of(a));
        when(trackedAssetMapper.toResponse(a)).thenReturn(response("bitcoin"));

        List<TrackedAssetResponse> result = service.getTrackedAssets(TrackedAssetType.CRYPTO);

        assertThat(result).extracting(TrackedAssetResponse::getAssetCode).containsExactly("bitcoin");
    }

    @Test
    void searchTrackedAssets_returnsEmptyList_whenTypesNull() {
        List<TrackedAssetResponse> result = service.searchTrackedAssets(null, "foo", null, null);

        assertThat(result).isEmpty();
        verify(trackedAssetRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void searchTrackedAssets_returnsEmptyList_whenTypesEmpty() {
        List<TrackedAssetResponse> result = service.searchTrackedAssets(List.of(), "foo", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void searchTrackedAssets_appliesSearchAndSort_whenInputsValid() {
        TrackedAsset a = asset("bitcoin", "Bitcoin", "BTCUSDT");
        when(trackedAssetRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(a));
        when(trackedAssetMapper.toResponse(a)).thenReturn(response("bitcoin"));

        List<TrackedAssetResponse> result = service.searchTrackedAssets(
                List.of(TrackedAssetType.CRYPTO), "bit", "assetCode", "asc");

        assertThat(result).hasSize(1);
    }

    @Test
    void searchTrackedAssets_supportsDisplayNameSort_andDescDirection() {
        when(trackedAssetRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());

        service.searchTrackedAssets(List.of(TrackedAssetType.CRYPTO), null, "displayName", "desc");

        verify(trackedAssetRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void searchTrackedAssets_fallsBackToSortOrderDefault_whenSortByUnknown() {
        when(trackedAssetRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());

        service.searchTrackedAssets(List.of(TrackedAssetType.CRYPTO), null, "unknownField", null);

        verify(trackedAssetRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void getTrackedAsset_returnsMappedResponse_whenFound() {
        TrackedAsset a = asset("bitcoin", "Bitcoin", "BTCUSDT");
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.of(a));
        when(trackedAssetMapper.toResponse(a)).thenReturn(response("bitcoin"));

        Optional<TrackedAssetResponse> result = service.getTrackedAsset(TrackedAssetType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
    }

    @Test
    void getTrackedAsset_returnsEmpty_whenRepositoryEmpty() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.empty());

        Optional<TrackedAssetResponse> result = service.getTrackedAsset(TrackedAssetType.CRYPTO, "missing");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveCodeOrThrow_returnsNormalizedCode_whenExists() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.of(asset("bitcoin", "Bitcoin", "BTCUSDT")));

        String resolved = service.resolveCodeOrThrow(TrackedAssetType.CRYPTO, "Bitcoin");

        assertThat(resolved).isEqualTo("bitcoin");
    }

    @Test
    void resolveCodeOrThrow_raises_whenNotFound() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCodeOrThrow(TrackedAssetType.CRYPTO, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCodes_delegatesToCache() {
        when(codeCache.get(TrackedAssetType.CRYPTO)).thenReturn(List.of("bitcoin", "ethereum"));

        List<String> codes = service.getCodes(TrackedAssetType.CRYPTO);

        assertThat(codes).containsExactly("bitcoin", "ethereum");
    }

    @Test
    void getDisplayNameMap_delegatesToCache() {
        when(codeCache.getDisplayNames(TrackedAssetType.CRYPTO))
                .thenReturn(Map.of("bitcoin", "Bitcoin"));

        Map<String, String> result = service.getDisplayNameMap(TrackedAssetType.CRYPTO);

        assertThat(result).containsEntry("bitcoin", "Bitcoin");
    }

    @Test
    void curatedDisplayName_returnsCuratedName_whenPresent() {
        when(codeCache.getDisplayNames(TrackedAssetType.CRYPTO))
                .thenReturn(Map.of("bitcoin", "Bitcoin"));

        String name = service.curatedDisplayName(MarketType.CRYPTO, "BITCOIN");

        assertThat(name).isEqualTo("Bitcoin");
    }

    @Test
    void curatedDisplayName_returnsNull_whenNoCuratedNameSet() {
        when(codeCache.getDisplayNames(TrackedAssetType.CRYPTO)).thenReturn(Map.of());

        String name = service.curatedDisplayName(MarketType.CRYPTO, "bitcoin");

        assertThat(name).isNull();
    }

    @Test
    void curatedDisplayName_returnsNull_whenCodeBlank() {
        String name = service.curatedDisplayName(MarketType.CRYPTO, "  ");

        assertThat(name).isNull();
        verify(codeCache, never()).getDisplayNames(any());
    }

    @Test
    void getCryptoBinanceSymbol_returnsTrimmedSymbol_whenPresent() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.of(asset("bitcoin", "Bitcoin", "  BTCUSDT  ")));

        Optional<String> result = service.getCryptoBinanceSymbol("bitcoin");

        assertThat(result).contains("BTCUSDT");
    }

    @Test
    void getCryptoBinanceSymbol_returnsEmpty_whenSymbolBlank() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.of(asset("bitcoin", "Bitcoin", "   ")));

        Optional<String> result = service.getCryptoBinanceSymbol("bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void getCryptoBinanceSymbol_returnsEmpty_whenAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(eq(TrackedAssetType.CRYPTO), any()))
                .thenReturn(Optional.empty());

        Optional<String> result = service.getCryptoBinanceSymbol("missing");

        assertThat(result).isEmpty();
    }
}
