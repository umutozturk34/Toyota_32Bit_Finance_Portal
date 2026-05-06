package com.finance.notification.market;

import com.finance.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/market-status")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MarketStatusController {

    private final MarketSessionResolver resolver;
    private final Clock clock;

    @GetMapping
    public ApiResponse<List<MarketStatusResponse>> listAll() {
        Instant now = clock.instant();
        List<MarketStatusResponse> snapshot = new ArrayList<>(SessionMarket.values().length);
        for (SessionMarket market : SessionMarket.values()) {
            resolver.resolve(market, now).ifPresent(session -> {
                Instant next = resolver.nextTransition(market, now).orElse(null);
                snapshot.add(MarketStatusResponse.of(market, session, next));
            });
        }
        return ApiResponse.success("Market status snapshot", snapshot);
    }
}
