package com.finance.backend.controller;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.service.ForexCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/v1/forex")
@RequiredArgsConstructor
public class ForexController {
    private final ForexCacheService forexCacheService;
    @GetMapping("/{currencyCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Forex> getForexByCurrencyCode(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        log.debug("Get forex snapshot: {}", normalized);
        return ResponseEntity.ok(forexCacheService.getForexSnapshot(normalized));
    }
    @GetMapping("/{currencyCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ForexCandle>> getForexHistory(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        log.debug("Get forex history: {}", normalized);
        return ResponseEntity.ok(forexCacheService.getForexHistory(normalized));
    }
}
