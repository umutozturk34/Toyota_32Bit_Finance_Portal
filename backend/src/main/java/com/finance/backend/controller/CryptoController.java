package com.finance.backend.controller;

import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.service.CryptoCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller for Crypto market data
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class CryptoController {
    
    private final CryptoCacheService cryptoCacheService;
    
    /**
     * Get crypto snapshot by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Crypto> getCryptoById(@PathVariable String id) {
        log.info("📊 API Request: Get crypto snapshot for: {}", id);
        
        Crypto crypto = cryptoCacheService.getCryptoById(id);
        
        if (crypto == null) {
            log.warn("⚠️ Crypto not found: {}", id);
            return ResponseEntity.notFound().build();
        }
        
        log.info("✅ Returned crypto: {} - ${}", crypto.getName(), crypto.getCurrentPrice());
        return ResponseEntity.ok(crypto);
    }
    
    /**
     * Get crypto candle history for charts
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CryptoCandle>> getCryptoHistory(@PathVariable String id) {
        log.info("📈 API Request: Get crypto history for: {}", id);
        
        List<CryptoCandle> candles = cryptoCacheService.getCandleHistory(id);
        
        if (candles.isEmpty()) {
            log.warn("⚠️ No history found for: {}", id);
            return ResponseEntity.notFound().build();
        }
        
        log.info("✅ Returned {} candles for: {}", candles.size(), id);
        return ResponseEntity.ok(candles);
    }
}
