package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated read API for the tracked-asset registry: search/list and fetch a single asset by type+code. */
@RestController
@RequestMapping("/api/v1/tracked-assets")
@RequiredArgsConstructor
@Validated
public class TrackedAssetController {

    private final TrackedAssetQueryService trackedAssetQueryService;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TrackedAssetResponse>> getTrackedAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @Size(max = 64) String search,
            @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<TrackedAssetType> types = MarketRequestHelper.parseTrackedTypes(type);
        List<TrackedAssetResponse> data = trackedAssetQueryService.searchTrackedAssets(
                types, search, sort, direction);
        return ApiResponse.successOrEmpty(
                translator.translate("api.trackedAsset.listRetrieved"),
                translator.translate("api.trackedAsset.empty"),
                data);
    }

    @GetMapping("/{type}/{code}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackedAssetResponse> getTrackedAsset(
            @PathVariable TrackedAssetType type,
            @PathVariable String code
    ) {
        TrackedAssetResponse data = trackedAssetQueryService.getTrackedAsset(type, code)
            .orElseThrow(() -> new ResourceNotFoundException("error.trackedAsset.notFound", type, code));
        return ApiResponse.success(translator.translate("api.trackedAsset.retrieved"), data);
    }

}
