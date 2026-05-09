package com.finance.market.bond.controller;
import com.finance.market.bond.model.Bond;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.common.dto.ApiResponse;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.dto.response.BondResponse;
import com.finance.shared.dto.response.GroupCount;
import com.finance.common.dto.response.PagedResponse;
import com.finance.shared.model.CandlePeriod;
import com.finance.market.bond.service.BondQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
public class BondController {

    private final BondQueryService bondQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<BondResponse>> getAllBonds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String bondType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success("Bonds retrieved successfully",
                bondQueryService.search(search, bondType, sort, direction, page, size));
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GroupCount>> getDistinctBondTypes() {
        return ApiResponse.success("Bond types retrieved",
                bondQueryService.getTypeCounts());
    }

    @GetMapping("/{seriesCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BondResponse> getBondByCode(@PathVariable String seriesCode) {
        return ApiResponse.success("Bond retrieved successfully",
                bondQueryService.getByCode(seriesCode));
    }

    @GetMapping("/rate-history/{isinCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<BondRateResponse>> getRateHistory(
            @PathVariable String isinCode,
            @RequestParam(defaultValue = "ALL") CandlePeriod period) {
        return ApiResponse.success("Rate history retrieved",
                bondQueryService.getRateHistory(isinCode, period));
    }
}
