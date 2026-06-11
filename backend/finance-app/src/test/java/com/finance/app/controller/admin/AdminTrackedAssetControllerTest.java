package com.finance.app.controller.admin;

import com.finance.app.service.TrackedAssetAdminService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.market.core.dto.request.UpsertTrackedAssetRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminTrackedAssetControllerTest {

    @Mock private TrackedAssetAdminService trackedAssetAdminService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private Translator translator;

    private AdminTrackedAssetController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminTrackedAssetController(
                trackedAssetAdminService, trackedAssetQueryService, translator);
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
    void getTrackedAssets_delegatesToQueryService_andReturnsList() {
        when(trackedAssetQueryService.searchTrackedAssets(
                List.of(TrackedAssetType.STOCK), null, "sortOrder", "asc"))
                .thenReturn(List.of(sample()));

        ApiResponse<List<TrackedAssetResponse>> response =
                controller.getTrackedAssets("STOCK", null, "sortOrder", "asc");

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void upsertTrackedAsset_delegatesToAdminService() {
        UpsertTrackedAssetRequest request = new UpsertTrackedAssetRequest();
        request.setAssetType(TrackedAssetType.STOCK);
        request.setAssetCode("THYAO.IS");
        when(trackedAssetAdminService.upsert(request)).thenReturn(sample());

        ApiResponse<TrackedAssetResponse> response = controller.upsertTrackedAsset(request);

        assertThat(response.getData().getAssetCode()).isEqualTo("THYAO.IS");
        verify(trackedAssetAdminService).upsert(request);
    }

    @Test
    void updateSortOrders_invokesAdminService_andReturnsVoidSuccess() {
        BulkTrackedAssetOrderUpdateRequest request = new BulkTrackedAssetOrderUpdateRequest();

        ApiResponse<Void> response = controller.updateSortOrders(request);

        assertThat(response.isSuccess()).isTrue();
        verify(trackedAssetAdminService).updateSortOrders(request);
    }

    @Test
    void deleteTrackedAsset_invokesAdminService_withTypeAndCode() {
        ApiResponse<Void> response =
                controller.deleteTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS");

        assertThat(response.isSuccess()).isTrue();
        verify(trackedAssetAdminService).delete(TrackedAssetType.STOCK, "THYAO.IS");
    }

    @Test
    void setEnabled_invokesAdminService_withTypeCodeAndFlag() {
        ApiResponse<Void> response =
                controller.setEnabled(TrackedAssetType.STOCK, "THYAO.IS", false);

        assertThat(response.isSuccess()).isTrue();
        verify(trackedAssetAdminService).setEnabled(TrackedAssetType.STOCK, "THYAO.IS", false);
    }
}
