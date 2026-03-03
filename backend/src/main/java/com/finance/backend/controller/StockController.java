package com.finance.backend.controller;
import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.mapper.ResponseMapper;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.service.MarketCacheService;
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
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final ResponseMapper responseMapper;
    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockResponse> getStockBySymbol(@PathVariable String symbol) {
        String normalized = symbol.strip().toUpperCase();
        log.debug("Get stock snapshot: {}", normalized);
        return ResponseEntity.ok(responseMapper.toStockResponse(stockCacheService.getSnapshot(normalized)));
    }
    @GetMapping("/{symbol}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandleResponse>> getStockHistory(@PathVariable String symbol) {
        String normalized = symbol.strip().toUpperCase();
        log.debug("Get stock history: {}", normalized);
        return ResponseEntity.ok(responseMapper.toStockCandleResponses(stockCacheService.getHistory(normalized)));
    }
}
