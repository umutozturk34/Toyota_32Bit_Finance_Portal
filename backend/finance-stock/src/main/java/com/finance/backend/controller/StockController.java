package com.finance.backend.controller;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.StockResponse;
import com.finance.backend.mapper.StockResponseMapper;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockResponseMapper stockResponseMapper;

    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockResponse> getStockBySymbol(@PathVariable String symbol) {
        String normalized = symbol.strip().toUpperCase();
        return ResponseEntity.ok(stockResponseMapper.toStockResponse(stockCacheService.getSnapshot(normalized)));
    }

    @GetMapping("/{symbol}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandleResponse>> getStockHistory(@PathVariable String symbol) {
        String normalized = symbol.strip().toUpperCase();
        return ResponseEntity.ok(stockResponseMapper.toStockCandleResponses(stockCacheService.getHistory(normalized)));
    }
}
