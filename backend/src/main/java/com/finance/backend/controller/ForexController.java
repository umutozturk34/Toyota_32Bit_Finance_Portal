package com.finance.backend.controller;
import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.ForexResponse;
import com.finance.backend.mapper.ResponseMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.service.MarketCacheService;
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
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final ResponseMapper responseMapper;
    @GetMapping("/{currencyCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ForexResponse> getForexByCurrencyCode(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        log.debug("Get forex snapshot: {}", normalized);
        return ResponseEntity.ok(responseMapper.toForexResponse(forexCacheService.getSnapshot(normalized)));
    }
    @GetMapping("/{currencyCode}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandleResponse>> getForexHistory(@PathVariable String currencyCode) {
        String normalized = currencyCode.strip().toUpperCase();
        log.debug("Get forex history: {}", normalized);
        return ResponseEntity.ok(responseMapper.toForexCandleResponses(forexCacheService.getHistory(normalized)));
    }
}
