package com.finance.backend.controller;

import com.finance.backend.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/trigger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final MarketDataService marketDataService;
    
    /**
     * Trigger ONLY snapshot update (current prices)
     * 
     * POST /api/v1/admin/trigger/snapshot
     * Runs asynchronously - returns immediately
     * Security: Requires ADMIN role
     */
    @PostMapping("/snapshot")
    public ResponseEntity<Map<String, String>> triggerSnapshotUpdate() {
        log.info("🔧 Admin triggered: Snapshot update");
        
        // Run async to prevent UI blocking
        new Thread(() -> {
            try {
                marketDataService.updateOnlySnapshots();
            } catch (Exception e) {
                log.error("Async snapshot update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Snapshot update started in background");
        response.put("type", "snapshot");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Trigger ONLY candle update (OHLC data)
     * 
     * POST /api/v1/admin/trigger/candles
     * Runs asynchronously - returns immediately
     * Rate limited: 2 seconds between coin requests
     * Security: Requires ADMIN role
     */
    @PostMapping("/candles")
    public ResponseEntity<Map<String, String>> triggerCandleUpdate() {
        log.info("🔧 Admin triggered: Candle update");
        
        // Run async to prevent UI blocking
        new Thread(() -> {
            try {
                marketDataService.updateOnlyCandles();
            } catch (Exception e) {
                log.error("Async candle update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Candle update started in background (rate limited: 2s per coin)");
        response.put("type", "candles");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Trigger FULL market update (both snapshots and candles)
     * 
     * POST /api/v1/admin/trigger/full
     * Runs asynchronously - returns immediately
     * Executes: updateOnlySnapshots() → updateOnlyCandles()
     * Security: Requires ADMIN role
     */
    @PostMapping("/full")
    public ResponseEntity<Map<String, String>> triggerFullUpdate() {
        log.info("🔧 Admin triggered: FULL market update");
        
        // Run async to prevent UI blocking
        new Thread(() -> {
            try {
                marketDataService.fullMarketUpdate();
            } catch (Exception e) {
                log.error("Async full update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Full market update started in background (snapshots + candles)");
        response.put("type", "full");
        
        return ResponseEntity.ok(response);
    }
}
