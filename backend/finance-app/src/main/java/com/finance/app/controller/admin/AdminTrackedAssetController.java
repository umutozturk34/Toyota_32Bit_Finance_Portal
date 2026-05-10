package com.finance.app.controller.admin;

import com.finance.app.controller.MarketRequestHelper;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.core.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.market.core.dto.request.UpsertTrackedAssetRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.model.TrackedAssetType;
import com.finance.app.service.TrackedAssetAdminService;
import com.finance.market.core.service.TrackedAssetQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tracked-assets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrackedAssetController {

    private final TrackedAssetAdminService trackedAssetAdminService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final Translator translator;

    @GetMapping
    public ApiResponse<List<TrackedAssetResponse>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetQueryService.searchTrackedAssets(
                types, search, sort, direction);
        return ApiResponse.success(translator.translate("api.trackedAsset.listRetrieved"), data);
    }

    @PostMapping
    public ApiResponse<TrackedAssetResponse> upsertTrackedAsset(
            @Valid @RequestBody UpsertTrackedAssetRequest request
    ) {
        TrackedAssetResponse data = trackedAssetAdminService.upsert(request);
        return ApiResponse.success(translator.translate("api.trackedAsset.saved"), data);
    }

    @PatchMapping("/order")
    public ApiResponse<Void> updateSortOrders(
            @Valid @RequestBody BulkTrackedAssetOrderUpdateRequest request
    ) {
        trackedAssetAdminService.updateSortOrders(request);
        return ApiResponse.success(translator.translate("api.trackedAsset.orderUpdated"), null);
    }

    @DeleteMapping("/{type}/{code}")
    public ApiResponse<Void> deleteTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        trackedAssetAdminService.delete(type, code);
        return ApiResponse.success(translator.translate("api.trackedAsset.deleted"), null);
    }

}
