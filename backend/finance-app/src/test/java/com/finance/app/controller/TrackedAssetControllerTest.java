package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.i18n.Translator;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackedAssetControllerTest {

    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private Translator translator;

    private TrackedAssetController controller;

    @BeforeEach
    void setUp() {
        controller = new TrackedAssetController(trackedAssetQueryService, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private TrackedAssetResponse sample() {
        return TrackedAssetResponse.builder()
                .assetType(TrackedAssetType.STOCK)
                .assetCode("THYAO.IS")
                .displayName("Türk Hava Yolları")
                .sortOrder(1)
                .build();
    }

    @Test
    void getTrackedAssets_delegatesToService_withParsedTypes() {
        when(trackedAssetQueryService.searchTrackedAssets(
                List.of(TrackedAssetType.STOCK), null, "sortOrder", "asc"))
                .thenReturn(List.of(sample()));

        ApiResponse<List<TrackedAssetResponse>> response =
                controller.getTrackedAssets("STOCK", null, "sortOrder", "asc");

        assertThat(response.getData()).hasSize(1);
        verify(trackedAssetQueryService).searchTrackedAssets(
                List.of(TrackedAssetType.STOCK), null, "sortOrder", "asc");
    }

    @Test
    void getTrackedAssets_passesNullSearchToService_whenTypeMissing() {
        when(trackedAssetQueryService.searchTrackedAssets(
                java.util.Arrays.asList(TrackedAssetType.values()), "term", "sortOrder", "asc"))
                .thenReturn(List.of());

        ApiResponse<List<TrackedAssetResponse>> response =
                controller.getTrackedAssets(null, "term", "sortOrder", "asc");

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void getTrackedAsset_returnsAsset_whenFound() {
        when(trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS"))
                .thenReturn(Optional.of(sample()));

        ApiResponse<TrackedAssetResponse> response =
                controller.getTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS");

        assertThat(response.getData().getAssetCode()).isEqualTo("THYAO.IS");
    }

    @Test
    void getTrackedAsset_throwsResourceNotFound_whenAbsent() {
        when(trackedAssetQueryService.getTrackedAsset(TrackedAssetType.STOCK, "MISSING.IS"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getTrackedAsset(TrackedAssetType.STOCK, "MISSING.IS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
