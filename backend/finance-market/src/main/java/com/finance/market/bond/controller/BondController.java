package com.finance.market.bond.controller;



import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.shared.dto.response.GroupCount;
import com.finance.common.dto.response.PagedResponse;
import com.finance.shared.model.CandlePeriod;
import com.finance.market.bond.service.BondQueryService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Authenticated read API for bonds: paged list/search, type counts, single bond, and rate history. */
@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
@Validated
public class BondController {

    private final BondQueryService bondQueryService;
    private final Translator translator;

    /** Paged bond list with optional name/code {@code search}, {@code bondType} filter, and sort. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<BondResponse>> getAllBonds(
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) String bondType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(translator.translate("api.bond.listRetrieved"),
                bondQueryService.search(search, bondType, sort, direction, page, size));
    }

    /** The distinct bond types with how many bonds each holds — drives the type filter facets. */
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getDistinctBondTypes() {
        return ApiResponse.success(translator.translate("api.bond.typesRetrieved"),
                bondQueryService.getTypeCounts());
    }

    /** Single bond looked up by its series code. */
    @GetMapping("/{seriesCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BondResponse> getBondByCode(@PathVariable String seriesCode) {
        return ApiResponse.success(translator.translate("api.bond.retrieved"),
                bondQueryService.getByCode(seriesCode));
    }

    /** Rate (yield) history for a bond keyed by ISIN over the given {@code period} (default {@code ALL}). */
    @GetMapping("/rate-history/{isinCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<BondRateResponse>> getRateHistory(
            @PathVariable String isinCode,
            @RequestParam(defaultValue = "ALL") CandlePeriod period) {
        return ApiResponse.success(translator.translate("api.bond.rateHistoryRetrieved"),
                bondQueryService.getRateHistory(isinCode, period));
    }
}
