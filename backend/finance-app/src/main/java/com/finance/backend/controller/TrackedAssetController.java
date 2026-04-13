package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.service.TrackedAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tracked-assets")
@RequiredArgsConstructor
public class TrackedAssetController {

    private final TrackedAssetService trackedAssetService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TrackedAssetResponse>>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = parseTypes(type);
        List<TrackedAssetResponse> data = trackedAssetService.searchTrackedAssets(
                types, false, search, sort, direction);
        return ResponseEntity.ok(ApiResponse.success("Tracked assets retrieved successfully", data));
    }

    @GetMapping("/{type}/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TrackedAssetResponse>> getTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        TrackedAssetResponse data = trackedAssetService.getTrackedAsset(type, code)
            .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + code));
        return ResponseEntity.ok(ApiResponse.success("Tracked asset retrieved successfully", data));
    }

    private List<TrackedAssetType> parseTypes(String type) {
        if (type == null || type.isBlank()) {
            return List.of(TrackedAssetType.values());
        }
        return Arrays.stream(type.split(","))
                .map(String::trim)
                .map(TrackedAssetType::valueOf)
                .toList();
    }
}
