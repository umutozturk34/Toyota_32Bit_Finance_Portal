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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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

/** Admin-only API to list, upsert, reorder, enable/disable and delete tracked-asset registry entries. */
@RestController
@RequestMapping("/api/v1/admin/tracked-assets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminTrackedAssetController {

    private final TrackedAssetAdminService trackedAssetAdminService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final Translator translator;

    /**
     * Lists tracked-asset entries, optionally filtered by a comma-separated {@code type} list and a free-text
     * {@code search}, sorted by {@code sort}/{@code direction} (defaults to ascending registry sort order).
     */
    @GetMapping
    public ApiResponse<List<TrackedAssetResponse>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @Size(max = 64) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetQueryService.searchTrackedAssets(
                types, search, sort, direction);
        return ApiResponse.success(translator.translate("api.trackedAsset.listRetrieved"), data);
    }

    /** Creates or updates a tracked-asset entry (keyed by type + code). */
    @PostMapping
    public ApiResponse<TrackedAssetResponse> upsertTrackedAsset(
            @Valid @RequestBody UpsertTrackedAssetRequest request
    ) {
        TrackedAssetResponse data = trackedAssetAdminService.upsert(request);
        return ApiResponse.success(translator.translate("api.trackedAsset.saved"), data);
    }

    /** Bulk-reorders tracked assets, applying the new display sort order from a single drag-and-drop save. */
    @PatchMapping("/order")
    public ApiResponse<Void> updateSortOrders(
            @Valid @RequestBody BulkTrackedAssetOrderUpdateRequest request
    ) {
        trackedAssetAdminService.updateSortOrders(request);
        return ApiResponse.success(translator.translate("api.trackedAsset.orderUpdated"), null);
    }

    /** Enables or disables the tracked asset; a disabled entry is hidden but survives auto-discovery (unlike delete). */
    @PatchMapping("/{type}/{code}/enabled")
    public ApiResponse<Void> setEnabled(
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @RequestParam boolean enabled
    ) {
        trackedAssetAdminService.setEnabled(type, code, enabled);
        return ApiResponse.success(translator.translate("api.trackedAsset.statusUpdated"), null);
    }

    /** Deletes the tracked asset; auto-discovered entries may reappear on the next discovery run (use disable to suppress). */
    @DeleteMapping("/{type}/{code}")
    public ApiResponse<Void> deleteTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        trackedAssetAdminService.delete(type, code);
        return ApiResponse.success(translator.translate("api.trackedAsset.deleted"), null);
    }

}
