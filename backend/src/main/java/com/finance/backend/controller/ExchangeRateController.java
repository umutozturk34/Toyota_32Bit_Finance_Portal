package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.entity.ExchangeRate;
import com.finance.backend.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {
    
    private final ExchangeRateService exchangeRateService;
    
    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExchangeRate>>> getLatestRates() {
        List<ExchangeRate> rates = exchangeRateService.getLatestRates();
        return ResponseEntity.ok(ApiResponse.success("Latest exchange rates retrieved successfully", rates));
    }
    
    @GetMapping("/{currencyCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ExchangeRate>> getRateByCurrency(
            @PathVariable String currencyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        LocalDate queryDate = date != null ? date : LocalDate.now();
        
        return exchangeRateService.getRateByCurrency(currencyCode, queryDate)
                .map(rate -> ResponseEntity.ok(ApiResponse.success("Exchange rate found", rate)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{currencyCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExchangeRate>>> getRateHistory(@PathVariable String currencyCode) {
        List<ExchangeRate> history = exchangeRateService.getRateHistory(currencyCode);
        return ResponseEntity.ok(ApiResponse.success("Exchange rate history retrieved successfully", history));
    }
    
    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> fetchRates() {
        exchangeRateService.fetchAndStoreRates();
        return ResponseEntity.ok(ApiResponse.success("Fetch started", "Exchange rate fetch triggered"));
    }
}
