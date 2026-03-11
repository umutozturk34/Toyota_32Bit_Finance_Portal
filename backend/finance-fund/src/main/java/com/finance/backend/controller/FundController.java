package com.finance.backend.controller;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.dto.response.FundResponse;
import com.finance.backend.mapper.FundResponseMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Log4j2
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
        List<String> codes = fundRepository.findAllFundCodes();
        List<Fund> funds = new ArrayList<>(codes.size());
        for (String code : codes) {
            try {
                funds.add(fundCacheService.getSnapshot(code));
            } catch (Exception e) {
                log.debug("Fund {} not in cache", code);
            }
        }
        return ResponseEntity.ok(fundResponseMapper.toFundResponses(funds));
    }

    @GetMapping("/{fundCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FundResponse> getFundByCode(@PathVariable String fundCode) {
        String normalized = fundCode.strip().toUpperCase();
        return ResponseEntity.ok(fundResponseMapper.toFundResponse(fundCacheService.getSnapshot(normalized)));
    }

    @GetMapping("/{fundCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FundCandleResponse>> getFundHistory(@PathVariable String fundCode) {
        String normalized = fundCode.strip().toUpperCase();
        return ResponseEntity.ok(fundResponseMapper.toFundCandleResponses(fundCacheService.getHistory(normalized)));
    }
}
