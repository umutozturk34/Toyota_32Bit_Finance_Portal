package com.finance.app.analytics.controller;

import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.analytics.service.ScenarioService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ScenarioService scenarioService;
    private final InflationBeaterService inflationBeaterService;
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
            @RequestParam(required = false) String benchmark) {
        return ApiResponse.success(translator.translate("api.analytics.inflationBeatersComputed"),
                inflationBeaterService.rank(period, benchmark));
    }
}
