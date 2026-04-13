package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.backend.dto.request.UpsertTrackedAssetRequest;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.service.TrackedAssetAdminService;
import com.finance.backend.service.TrackedAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final TrackedAssetService trackedAssetService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrackedAssetResponse>>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetService.searchTrackedAssets(
                types, includeDisabled, search, sort, direction);
        return ResponseEntity.ok(ApiResponse.success("Tracked assets retrieved successfully", data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TrackedAssetResponse>> upsertTrackedAsset(
            @Valid @RequestBody UpsertTrackedAssetRequest request
    ) {
        TrackedAssetResponse data = trackedAssetAdminService.upsert(request);
        return ResponseEntity.ok(ApiResponse.success("Tracked asset saved successfully", data));
    }

    @PatchMapping("/{type}/{code}/enabled")
    public ResponseEntity<ApiResponse<Void>> setEnabled(
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @RequestParam boolean enabled
    ) {
        trackedAssetAdminService.setEnabled(type, code, enabled);
        return ResponseEntity.ok(ApiResponse.success("Tracked asset status updated", null));
    }

    @PatchMapping("/order")
    public ResponseEntity<ApiResponse<Void>> updateSortOrders(
            @Valid @RequestBody BulkTrackedAssetOrderUpdateRequest request
    ) {
        trackedAssetAdminService.updateSortOrders(request);
        return ResponseEntity.ok(ApiResponse.success("Tracked asset order updated", null));
    }

    @DeleteMapping("/{type}/{code}")
    public ResponseEntity<ApiResponse<Void>> deleteTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        trackedAssetAdminService.delete(type, code);
        return ResponseEntity.ok(ApiResponse.success("Tracked asset deleted", null));
    }

}
