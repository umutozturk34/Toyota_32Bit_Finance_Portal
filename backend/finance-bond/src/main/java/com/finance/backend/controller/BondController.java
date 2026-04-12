package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.BondRateResponse;
import com.finance.backend.dto.response.BondResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.service.BondQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
public class BondController {

    private final BondQueryService bondQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<BondResponse>>> getAllBonds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String bondType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success("Bonds retrieved successfully",
                bondQueryService.search(search, bondType, sort, direction, page, size)));
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDistinctBondTypes() {
        return ResponseEntity.ok(ApiResponse.success("Bond types retrieved",
                bondQueryService.getTypeCounts()));
    }

    @GetMapping("/{seriesCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BondResponse>> getBondByCode(@PathVariable String seriesCode) {
        return ResponseEntity.ok(ApiResponse.success("Bond retrieved successfully",
                bondQueryService.getByCode(seriesCode)));
    }

    @GetMapping("/rate-history/{isinCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BondRateResponse>>> getRateHistory(
            @PathVariable String isinCode,
            @RequestParam(defaultValue = "ALL") CandlePeriod period) {
        return ResponseEntity.ok(ApiResponse.success("Rate history retrieved",
                bondQueryService.getRateHistory(isinCode, period)));
    }
}
