package com.finance.backend.controller;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.CryptoResponse;
import com.finance.backend.mapper.CryptoResponseMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class CryptoController {
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoResponseMapper cryptoResponseMapper;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CryptoResponse> getCryptoById(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        log.debug("Get crypto snapshot: {}", normalizedId);
        return ResponseEntity.ok(cryptoResponseMapper.toCryptoResponse(cryptoCacheService.getSnapshot(normalizedId)));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandleResponse>> getCryptoHistory(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        log.debug("Get crypto history: {}", normalizedId);
        return ResponseEntity.ok(cryptoResponseMapper.toCryptoCandleResponses(cryptoCacheService.getHistory(normalizedId)));
    }
}
