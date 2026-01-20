package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.entity.CryptoPrice;
import com.finance.backend.service.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
public class CryptoController {
    
    private final CryptoService cryptoService;
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CryptoPrice>>> getLatestCryptos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<CryptoPrice> cryptos = cryptoService.getLatestCryptos(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Cryptocurrencies retrieved successfully", cryptos));
    }
    
    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CryptoPrice>> getCryptoBySymbol(@PathVariable String symbol) {
        CryptoPrice crypto = cryptoService.getLatestCryptoBySymbol(symbol);
        
        if (crypto == null) {
            return ResponseEntity.ok(ApiResponse.error("Cryptocurrency not found"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Cryptocurrency retrieved successfully", crypto));
    }
    
    @GetMapping("/{symbol}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CryptoPrice>>> getCryptoHistory(@PathVariable String symbol) {
        List<CryptoPrice> history = cryptoService.getCryptoHistory(symbol);
        return ResponseEntity.ok(ApiResponse.success("Crypto history retrieved successfully", history));
    }
    
    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> fetchCryptos() {
        cryptoService.fetchAndStoreCryptoPrices();
        return ResponseEntity.ok(ApiResponse.success("Crypto prices fetch initiated", null));
    }
}
