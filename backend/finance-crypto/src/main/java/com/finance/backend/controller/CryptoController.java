package com.finance.backend.controller;

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
    public ResponseEntity<List<CryptoResponse>> getAllCryptos() {
        return ResponseEntity.ok(cryptoResponseMapper.toCryptoResponses(cryptoCacheService.getAllSnapshots()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CryptoResponse> getCryptoById(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        return ResponseEntity.ok(cryptoResponseMapper.toCryptoResponse(cryptoCacheService.getSnapshot(normalizedId)));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandleResponse>> getCryptoHistory(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        return ResponseEntity.ok(cryptoResponseMapper.toCryptoCandleResponses(cryptoCacheService.getHistory(normalizedId)));
    }
}
