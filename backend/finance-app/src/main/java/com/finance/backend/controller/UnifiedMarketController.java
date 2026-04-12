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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Parameter(description = "Asset types (comma-separated)", schema = @Schema(allowableValues = {"STOCK", "CRYPTO", "FOREX", "FUND"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "Single asset code lookup", example = "THYAO.IS")
            @RequestParam(required = false) String code,
            @Parameter(description = "Stock segment filter", schema = @Schema(allowableValues = {"EQUITY", "MAIN_INDEX", "SECONDARY_INDEX"}))
            @RequestParam(required = false) String segment,
            @Parameter(description = "Search by code or name")
            @RequestParam(required = false) String search,
            @Parameter(description = "Fund type filter", schema = @Schema(allowableValues = {"BYF", "YAT"}))
            @RequestParam(required = false) String subType,
            @Parameter(description = "Sort field", schema = @Schema(allowableValues = {"price", "changePercent", "name"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String direction,
            @Parameter(description = "Filter by change", schema = @Schema(allowableValues = {"all", "gainers", "losers"}))
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
            @Parameter(description = "Max items per category")
            @RequestParam(required = false) Integer limit) {
        int resolvedLimit = resolveLimit(limit, appProperties.getPagination().getMarket());

        return ResponseEntity.ok(ApiResponse.success("Market overview retrieved successfully",
                unifiedMarketService.getOverview(resolvedLimit)));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<?>>> getMarketHistory(
            @RequestParam MarketType type,
            @Parameter(description = "Asset code", example = "THYAO.IS")
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
        try {
            return Arrays.stream(type.split(",")).map(String::trim).map(String::toUpperCase).map(MarketType::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new com.finance.backend.exception.BadRequestException("Invalid market type: " + type);
        }
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
