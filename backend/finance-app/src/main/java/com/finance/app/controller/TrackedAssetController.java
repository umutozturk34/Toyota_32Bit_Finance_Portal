package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<List<TrackedAssetResponse>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetQueryService.searchTrackedAssets(
                types, search, sort, direction);
        return ApiResponse.successOrEmpty("Tracked assets retrieved successfully", "No tracked assets found", data);
    }

    @GetMapping("/{type}/{code}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackedAssetResponse> getTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        TrackedAssetResponse data = trackedAssetQueryService.getTrackedAsset(type, code)
            .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + code));
        return ApiResponse.success("Tracked asset retrieved successfully", data);
    }

}
