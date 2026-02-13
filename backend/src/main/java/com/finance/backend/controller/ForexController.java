package com.finance.backend.controller;

import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.service.ForexCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/forex")
@RequiredArgsConstructor
public class ForexController {
    
    private final ForexCacheService forexCacheService;

    @GetMapping("/{currencyCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Forex> getForexByCurrencyCode(@PathVariable String currencyCode) {
        log.info("API Request: Get forex snapshot for: {}", currencyCode);
        Forex forex = forexCacheService.getForexSnapshot(currencyCode);
        log.info("Returned forex: {} - TRY {} | yahooUpdatedAt={} | tcmbUpdatedAt={}", 
            forex.getCurrencyCode(), forex.getCurrentPrice(), 
            forex.getYahooUpdatedAt(), forex.getTcmbUpdatedAt());
        return ResponseEntity.ok(forex);
    }

    @GetMapping("/{currencyCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ForexCandle>> getForexHistory(@PathVariable String currencyCode) {
        log.info("API Request: Get forex history for: {}", currencyCode);
        List<ForexCandle> candles = forexCacheService.getForexHistory(currencyCode);
        log.info("Returned {} candles for: {}", candles.size(), currencyCode);
        return ResponseEntity.ok(candles);
    }
}
