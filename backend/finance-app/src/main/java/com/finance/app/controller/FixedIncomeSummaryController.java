package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;
import com.finance.portfolio.fixedincome.FixedIncomeSummaryService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine bond) view: its headline
 * summary (totals + per-kind allocation) and a value-over-time series. Both endpoints are scoped to the
 * owning portfolio and the JWT subject; ownership lives in {@link FixedIncomeSummaryService}, which throws
 * {@code ResourceNotFoundException} (mapped to 404) when the portfolio is not owned, so this layer only
 * delegates and wraps the {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/fixed-income")
@RequiredArgsConstructor
@Validated
public class FixedIncomeSummaryController {

    private static final String DEFAULT_PERIOD = "1Y";

    private final FixedIncomeSummaryService service;
    private final Translator translator;

    @GetMapping("/summary")
    public ApiResponse<FixedIncomeSummaryResponse> summary(@PathVariable Long portfolioId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        FixedIncomeSummaryResponse data = service.summary(portfolioId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.fixedIncome.summaryRetrieved"), data);
    }

    @GetMapping("/history")
    public ApiResponse<List<FixedIncomeHistoryPoint>> history(
            @PathVariable Long portfolioId,
            @Parameter(description = "Period window",
                    schema = @Schema(allowableValues = {"1M", "6M", "1Y", "3Y", "5Y", "ALL"}))
            @RequestParam(defaultValue = DEFAULT_PERIOD) String period,
            @AuthenticationPrincipal Jwt jwt) {
        List<FixedIncomeHistoryPoint> data = service.history(portfolioId, jwt.getSubject(), period);
        return ApiResponse.success(translator.translate("api.portfolio.fixedIncome.historyRetrieved"), data);
    }
}
