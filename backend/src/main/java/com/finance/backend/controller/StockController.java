package com.finance.backend.controller;

import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {
    
    private final StockCacheService stockCacheService;
    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Stock> getStockBySymbol(@PathVariable String symbol) {
        log.info("API Request: Get stock snapshot for: {}", symbol);
        Stock stock = stockCacheService.getStockSnapshot(symbol);
        log.info("Returned stock: {} - TRY {}", stock.getSymbol(), stock.getCurrentPrice());
        return ResponseEntity.ok(stock);
    }
    
    @GetMapping("/{symbol}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StockCandle>> getStockHistory(@PathVariable String symbol) {
        log.info("API Request: Get stock history for: {}", symbol);
        List<StockCandle> candles = stockCacheService.getStockHistory(symbol);
        log.info("Returned {} candles for: {}", candles.size(), symbol);
        return ResponseEntity.ok(candles);
    }
}
