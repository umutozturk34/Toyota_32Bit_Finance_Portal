package com.finance.backend.controller;

import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final MarketDataService marketDataService;
    private final StockDataService stockDataService;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    
    @PostMapping("/trigger/crypto/snapshot")
    public ResponseEntity<Map<String, String>> triggerCryptoSnapshotUpdate() {
        log.info("Admin triggered: Crypto snapshot update");
        
        new Thread(() -> {
            try {
                marketDataService.updateOnlySnapshots();
            } catch (Exception e) {
                log.error("Async crypto snapshot update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Crypto snapshot update started in background");
        response.put("type", "crypto-snapshot");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/crypto/candles")
    public ResponseEntity<Map<String, String>> triggerCryptoCandleUpdate() {
        log.info("Admin triggered: Crypto candle update");
        
        new Thread(() -> {
            try {
                marketDataService.updateOnlyCandles();
            } catch (Exception e) {
                log.error("Async crypto candle update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Crypto candle update started in background");
        response.put("type", "crypto-candles");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/crypto/full")
    public ResponseEntity<Map<String, String>> triggerFullCryptoUpdate() {
        log.info("Admin triggered: FULL crypto market update");
        
        new Thread(() -> {
            try {
                marketDataService.fullMarketUpdate();
            } catch (Exception e) {
                log.error("Async full crypto update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Full crypto market update started in background");
        response.put("type", "crypto-full");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/stock/snapshot")
    public ResponseEntity<Map<String, String>> triggerStockSnapshotUpdate() {
        log.info("Admin triggered: Stock snapshot update");
        
        new Thread(() -> {
            try {
                stockDataService.updateStockSnapshots();
            } catch (Exception e) {
                log.error("Async stock snapshot update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Stock snapshot update started in background");
        response.put("type", "stock-snapshot");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/trigger/stock/candles")
    public ResponseEntity<Map<String, String>> triggerStockCandleUpdate() {
        log.info("Admin triggered: Stock candle update");
        
        new Thread(() -> {
            try {
                stockDataService.updateStockCandles();
            } catch (Exception e) {
                log.error("Async stock candle update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Stock candle update started in background (5 years data)");
        response.put("type", "stock-candles");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/stock/full")
    public ResponseEntity<Map<String, String>> triggerFullStockUpdate() {
        log.info("Admin triggered: FULL stock market update");
        
        new Thread(() -> {
            try {
                stockDataService.updateStockSnapshots();
                stockDataService.updateStockCandles();
            } catch (Exception e) {
                log.error("Async full stock update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Full stock market update started in background");
        response.put("type", "stock-full");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/forex/snapshot")
    public ResponseEntity<Map<String, String>> triggerForexSnapshotUpdate() {
        log.info("Admin triggered: TCMB + Yahoo Forex SNAPSHOT update");
        
        new Thread(() -> {
            try {
                tcmbForexService.fetchAndSaveTcmbRates();
                yahooForexService.syncAllYahooSnapshots();
            } catch (Exception e) {
                log.error("Async forex snapshot update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "TCMB + Yahoo snapshot update started (~2 min)");
        response.put("type", "forex-snapshot");
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/trigger/forex/candles")
    public ResponseEntity<Map<String, String>> triggerForexCandleUpdate() {
        log.info("Admin triggered: Yahoo Finance CANDLES-ONLY update");
        
        new Thread(() -> {
            try {
                yahooForexService.syncAllYahooCandles();
            } catch (Exception e) {
                log.error("Async Yahoo forex candle update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Yahoo Finance candles update started (~10 min, 5 years OHLC)");
        response.put("type", "forex-candles");
        
        return ResponseEntity.ok(response);
    }
    
    
    @PostMapping("/trigger/forex/full")
    public ResponseEntity<Map<String, String>> triggerFullForexUpdate() {
        log.info("Admin triggered: FULL forex update");
        
        new Thread(() -> {
            try {
                tcmbForexService.fetchAndSaveTcmbRates();
                yahooForexService.syncAllYahooSnapshots();
                yahooForexService.syncAllYahooCandles();
            } catch (Exception e) {
                log.error("Async full forex update failed", e);
            }
        }).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Full forex update started (TCMB + Yahoo snapshots + 5y candles)");
        response.put("type", "forex-full");
        
        return ResponseEntity.ok(response);
    }
}