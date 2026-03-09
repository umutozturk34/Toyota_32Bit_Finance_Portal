package com.finance.backend.controller;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.dto.response.FundResponse;
import com.finance.backend.mapper.FundResponseMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/funds")
@RequiredArgsConstructor
public class FundController {

    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final FundResponseMapper fundResponseMapper;
    private final FundRepository fundRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FundResponse>> getAllFunds() {
        log.debug("Get all fund snapshots");
        List<Fund> funds = fundRepository.findAll();
        return ResponseEntity.ok(fundResponseMapper.toFundResponses(funds));
    }

    @GetMapping("/{fundCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FundResponse> getFundByCode(@PathVariable String fundCode) {
        String normalized = fundCode.strip().toUpperCase();
        log.debug("Get fund snapshot: {}", normalized);
        return ResponseEntity.ok(fundResponseMapper.toFundResponse(fundCacheService.getSnapshot(normalized)));
    }

    @GetMapping("/{fundCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FundCandleResponse>> getFundHistory(@PathVariable String fundCode) {
        String normalized = fundCode.strip().toUpperCase();
        log.debug("Get fund history: {}", normalized);
        return ResponseEntity.ok(fundResponseMapper.toFundCandleResponses(fundCacheService.getHistory(normalized)));
    }
}
