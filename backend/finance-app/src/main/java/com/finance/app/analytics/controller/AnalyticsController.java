package com.finance.app.analytics.controller;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.analytics.service.PortfolioSeriesProvider;
import com.finance.app.analytics.service.ScenarioService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for analytics: scenario simulation, inflation-beater rankings, and a portfolio's daily value
 * series. All endpoints require authentication; the portfolio series is scoped to the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Validated
public class AnalyticsController {

    private final ScenarioService scenarioService;
    private final InflationBeaterService inflationBeaterService;
    private final AssetReturnsService assetReturnsService;
    private final PortfolioSeriesProvider portfolioSeriesProvider;
    private final Translator translator;

    @PostMapping("/scenarios")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ScenarioResponse> simulate(@Valid @RequestBody ScenarioRequest request) {
        return ApiResponse.success(translator.translate("api.analytics.scenarioComputed"),
                scenarioService.simulate(request));
    }

    @GetMapping("/inflation-beaters")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InflationBeaterResponse> inflationBeaters(
            @Parameter(description = "Period window", schema = @Schema(allowableValues = {"1M", "6M", "1Y", "3Y", "5Y"}))
            @RequestParam(defaultValue = "1Y") String period,
            @Parameter(description = "Benchmark macro indicator code (default CPI)")
            @RequestParam(required = false) String benchmark,
            @Parameter(description = "Override comparison currency (TRY/USD/EUR); when null, derived from benchmark")
            @RequestParam(required = false) String targetCurrency) {
        return ApiResponse.success(translator.translate("api.analytics.inflationBeatersComputed"),
                inflationBeaterService.rank(period, benchmark, targetCurrency));
    }

    @GetMapping("/returns")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AssetReturnsResponse> assetReturns() {
        return ApiResponse.success(translator.translate("api.analytics.assetReturnsRetrieved"),
                assetReturnsService.getReturns());
    }

    @GetMapping("/portfolio-series/{portfolioId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<HistoryPoint>> portfolioSeries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Return cumulative profit/loss in TRY (the Kâr/Zarar Total line) — takes precedence over twr")
            @RequestParam(name = "pnl", defaultValue = "false") boolean pnl,
            @Parameter(description = "Return the capital-weighted cumulative-return index instead of raw value")
            @RequestParam(name = "twr", defaultValue = "false") boolean twr) {
        List<HistoryPoint> series;
        if (pnl) {
            series = portfolioSeriesProvider.dailyPnlSeries(portfolioId, jwt.getSubject(), from, to);
        } else if (twr) {
            series = portfolioSeriesProvider.dailyReturnIndexSeries(portfolioId, jwt.getSubject(), from, to);
        } else {
            series = portfolioSeriesProvider.dailyValueSeries(portfolioId, jwt.getSubject(), from, to);
        }
        return ApiResponse.success(translator.translate("api.analytics.portfolioSeriesRetrieved"), series);
    }
}
