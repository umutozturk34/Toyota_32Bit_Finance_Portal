package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.service.TrackedAssetQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tracked-assets")
@RequiredArgsConstructor
public class TrackedAssetController {

    private final TrackedAssetQueryService trackedAssetQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TrackedAssetResponse>>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetQueryService.searchTrackedAssets(
                types, false, search, sort, direction);
        return ResponseEntity.ok(ApiResponse.successOrEmpty("Tracked assets retrieved successfully", "No tracked assets found", data));
    }

    @GetMapping("/{type}/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TrackedAssetResponse>> getTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        TrackedAssetResponse data = trackedAssetQueryService.getTrackedAsset(type, code)
            .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + code));
        return ResponseEntity.ok(ApiResponse.success("Tracked asset retrieved successfully", data));
    }

}
