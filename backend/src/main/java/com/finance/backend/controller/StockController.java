package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.BistIndexDto;
import com.finance.backend.entity.StockPrice;
import com.finance.backend.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {
    
    private final StockService stockService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockPrice>>> getAllStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<StockPrice> stocks = stockService.getLatestStocks(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Stocks retrieved successfully", stocks));
    }
    
    @GetMapping("/us")
    public ResponseEntity<ApiResponse<Page<StockPrice>>> getUSStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<StockPrice> stocks = stockService.getLatestStocksByMarket("US", PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("US stocks retrieved successfully", stocks));
    }
    
    @GetMapping("/bist")
    public ResponseEntity<ApiResponse<Page<StockPrice>>> getBISTStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<StockPrice> stocks = stockService.getLatestStocksByMarket("BIST", PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("BIST stocks retrieved successfully", stocks));
    }
    
    @GetMapping("/bist-fund")
    public ResponseEntity<ApiResponse<Page<StockPrice>>> getBISTFunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<StockPrice> stocks = stockService.getLatestStocksByMarket("BIST-FUND", PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("BIST funds retrieved successfully", stocks));
    }
    
    @GetMapping("/bist-index")
    public ResponseEntity<ApiResponse<BistIndexDto>> getBISTIndex() {
        BistIndexDto index = stockService.getBistIndex();
        if (index == null) {
            return ResponseEntity.ok(ApiResponse.error("BIST index not available"));
        }
        return ResponseEntity.ok(ApiResponse.success("BIST index retrieved successfully", index));
    }
    
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<StockPrice>> getStockBySymbol(@PathVariable String symbol) {
        StockPrice stock = stockService.getLatestStockBySymbol(symbol.toUpperCase());
        
        if (stock == null) {
            return ResponseEntity.ok(ApiResponse.error("Stock not found"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Stock retrieved successfully", stock));
    }
    
    @GetMapping("/{symbol}/history")
    public ResponseEntity<ApiResponse<List<StockPrice>>> getStockHistory(@PathVariable String symbol) {
        List<StockPrice> history = stockService.getStockHistory(symbol.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success("Stock history retrieved successfully", history));
    }
    
    @PostMapping("/fetch/us")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> fetchUSStocks() {
        stockService.fetchAndStoreUsStocks();
        return ResponseEntity.ok(ApiResponse.success("US stocks fetch initiated", null));
    }
    
    @PostMapping("/fetch/bist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> fetchBISTStocks() {
        stockService.fetchAndStoreBISTStocksFromIsYatirim();
        return ResponseEntity.ok(ApiResponse.success("BIST stocks fetch initiated", null));
    }
}
