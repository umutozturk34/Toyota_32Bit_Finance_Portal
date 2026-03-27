package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class CryptoController {
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoResponseMapper cryptoResponseMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CryptoResponse>>> getAllCryptos() {
        List<CryptoResponse> cryptos = cryptoResponseMapper.toCryptoResponses(cryptoCacheService.getAllSnapshots());
        return ResponseEntity.ok(ApiResponse.success("Cryptos retrieved successfully", cryptos));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CryptoResponse>> getCryptoById(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        CryptoResponse crypto = cryptoResponseMapper.toCryptoResponse(cryptoCacheService.getSnapshot(normalizedId));
        return ResponseEntity.ok(ApiResponse.success("Crypto retrieved successfully", crypto));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getCryptoHistory(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        List<CandleResponse> history = cryptoResponseMapper.toCryptoCandleResponses(cryptoCacheService.getHistory(normalizedId));
        return ResponseEntity.ok(ApiResponse.success("Crypto history retrieved successfully", history));
    }
}
