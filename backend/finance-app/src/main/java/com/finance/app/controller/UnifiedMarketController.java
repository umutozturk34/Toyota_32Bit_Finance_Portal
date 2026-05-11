package com.finance.app.controller;




import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.shared.dto.response.GroupCount;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.dto.response.MarketAvailabilityResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.shared.model.CandlePeriod;
import com.finance.common.model.MarketType;
import com.finance.app.service.UnifiedMarketService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class UnifiedMarketController {

    private final AppProperties appProperties;
    private final UnifiedMarketService unifiedMarketService;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<MarketAssetResponse>> getMarketAssets(
            @Parameter(description = "Asset types (comma-separated)", schema = @Schema(allowableValues = {"STOCK", "CRYPTO", "FOREX", "FUND", "COMMODITY"}))
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

        AppProperties.Market market = appProperties.getPagination().getMarket();
        List<MarketType> types = MarketRequestHelper.parseMarketTypes(type);
        int resolvedSize = MarketRequestHelper.clamp(size, market.getDefaultSize(), market.getMaxSize());

        return ApiResponse.successOrEmpty(translator.translate("api.market.assetsRetrieved"), translator.translate("api.market.noData"),
                unifiedMarketService.search(types, code, segment, subType, search, sort, direction, filter, page, resolvedSize));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<?>> getMarketHistory(
            @RequestParam MarketType type,
            @Parameter(description = "Asset code", example = "THYAO.IS")
            @RequestParam String code,
            @RequestParam(defaultValue = "ALL") CandlePeriod period) {

        return ApiResponse.success(translator.translate("api.market.historyRetrieved"),
                unifiedMarketService.getHistory(type, code, period));
    }

    @GetMapping("/group-counts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getGroupCounts(
            @RequestParam MarketType type) {
        return ApiResponse.success(translator.translate("api.market.groupCountsRetrieved"),
                unifiedMarketService.getGroupCounts(type));
    }

    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MarketAvailabilityResponse> getMonthlyAvailability(
            @RequestParam MarketType type,
            @Parameter(description = "Asset code", example = "THYAO.IS")
            @RequestParam String code,
            @Parameter(description = "Year-month (yyyy-MM)", example = "2025-04")
            @RequestParam String yearMonth) {
        return ApiResponse.success(translator.translate("api.market.availabilityRetrieved"),
                unifiedMarketService.getMonthlyAvailability(type, code, yearMonth));
    }
}
