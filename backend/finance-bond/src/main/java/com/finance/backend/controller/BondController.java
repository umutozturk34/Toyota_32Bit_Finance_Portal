package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.BondRateResponse;
import com.finance.backend.dto.response.BondResponse;
import com.finance.backend.mapper.BondResponseMapper;
import com.finance.backend.model.Bond;
import com.finance.backend.model.BondRateHistory;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
public class BondController {
    private final MarketCacheService<Bond, BondRateHistory> bondCacheService;
    private final BondResponseMapper bondResponseMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BondResponse>>> getAllBonds() {
        List<BondResponse> bonds = bondResponseMapper.toBondResponses(bondCacheService.getAllSnapshots());
        return ResponseEntity.ok(ApiResponse.success("Bonds retrieved successfully", bonds));
    }

    @GetMapping("/{seriesCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BondResponse>> getBondByCode(@PathVariable String seriesCode) {
        BondResponse bond = bondResponseMapper.toBondResponse(bondCacheService.getSnapshot(seriesCode));
        return ResponseEntity.ok(ApiResponse.success("Bond retrieved successfully", bond));
    }

    @GetMapping("/rate-history/{isinCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BondRateResponse>>> getRateHistory(@PathVariable String isinCode) {
        List<BondRateHistory> history = bondCacheService.getHistory(isinCode);
        List<BondRateResponse> data = bondResponseMapper.toRateResponses(history);
        return ResponseEntity.ok(ApiResponse.success("Rate history retrieved", data));
    }
}
