package com.finance.backend.controller;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.service.CryptoCacheService;
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
    private final CryptoCacheService cryptoCacheService;
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Crypto> getCryptoById(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        log.debug("Get crypto snapshot: {}", normalizedId);
        return ResponseEntity.ok(cryptoCacheService.getCryptoById(normalizedId));
    }
    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CryptoCandle>> getCryptoHistory(@PathVariable String id) {
        String normalizedId = id.strip().toLowerCase();
        log.debug("Get crypto history: {}", normalizedId);
        return ResponseEntity.ok(cryptoCacheService.getCandleHistory(normalizedId));
    }
}
