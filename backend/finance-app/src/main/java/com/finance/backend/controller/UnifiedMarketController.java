package com.finance.backend.controller;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.MarketOverviewResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.UnifiedMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class UnifiedMarketController {

    private final AppProperties appProperties;
    private final UnifiedMarketService unifiedMarketService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<MarketAssetResponse>>> getMarketAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String subType,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {

        List<MarketType> types = parseTypes(type);
        int resolvedSize = resolveSize(size, appProperties.getPagination().getMarket());

        return ResponseEntity.ok(ApiResponse.success("Market assets retrieved successfully",
                unifiedMarketService.search(types, code, segment, subType, search, sort, direction, filter, page, resolvedSize)));
    }

    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MarketOverviewResponse>> getMarketOverview(
            @RequestParam(required = false) Integer limit) {
        int resolvedLimit = resolveLimit(limit, appProperties.getPagination().getMarket());

        return ResponseEntity.ok(ApiResponse.success("Market overview retrieved successfully",
                unifiedMarketService.getOverview(resolvedLimit)));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getMarketHistory(
            @RequestParam MarketType type,
            @RequestParam String code,
            @RequestParam(defaultValue = "ALL") CandlePeriod period) {

        return ResponseEntity.ok(ApiResponse.success("Market history retrieved successfully",
                unifiedMarketService.getHistory(type, code, period)));
    }

    @GetMapping("/group-counts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupCounts(
            @RequestParam MarketType type) {
        return ResponseEntity.ok(ApiResponse.success("Group counts retrieved",
                unifiedMarketService.getGroupCounts(type)));
    }

    private List<MarketType> parseTypes(String type) {
        if (type == null || type.isBlank()) return List.of(MarketType.values());
        return Arrays.stream(type.split(",")).map(String::trim).map(String::toUpperCase).map(MarketType::valueOf).toList();
    }

    private int resolveSize(Integer size, AppProperties.Market market) {
        int requested = size == null ? market.getDefaultSize() : size;
        return Math.max(1, Math.min(requested, market.getMaxSize()));
    }

    private int resolveLimit(Integer limit, AppProperties.Market market) {
        int resolved = limit == null ? market.getDefaultOverviewLimit() : limit;
        return Math.max(1, Math.min(resolved, market.getMaxOverviewLimit()));
    }
}
