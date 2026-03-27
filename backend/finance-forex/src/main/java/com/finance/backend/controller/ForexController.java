package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.mapper.ForexResponseMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1/forex")
@RequiredArgsConstructor
public class ForexController {
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final ForexResponseMapper forexResponseMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ForexResponse>>> getAllForex() {
        List<ForexResponse> forexList = forexResponseMapper.toForexResponses(forexCacheService.getAllSnapshots());
        return ResponseEntity.ok(ApiResponse.success("Forex pairs retrieved successfully", forexList));
    }

    @GetMapping("/{currencyCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ForexResponse>> getForexByCurrencyCode(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        ForexResponse forex = forexResponseMapper.toForexResponse(forexCacheService.getSnapshot(normalized));
        return ResponseEntity.ok(ApiResponse.success("Forex pair retrieved successfully", forex));
    }

    @GetMapping("/{currencyCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getForexHistory(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        List<CandleResponse> history = forexResponseMapper.toForexCandleResponses(forexCacheService.getHistory(normalized));
        return ResponseEntity.ok(ApiResponse.success("Forex history retrieved successfully", history));
    }
}
