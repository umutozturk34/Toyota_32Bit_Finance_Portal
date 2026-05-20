package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.mapper.MacroIndicatorResponseMapper;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1/macro-indicators")
@RequiredArgsConstructor
public class MacroIndicatorController {

    private static final int DEFAULT_HISTORY_YEARS = 5;

    private final MacroIndicatorQueryService queryService;
    private final MacroIndicatorResponseMapper mapper;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MacroIndicatorResponse>> list(
            @RequestParam(required = false) MacroCategory category,
            @RequestParam(required = false, defaultValue = "false") boolean prominentOnly) {
        List<MacroIndicator> indicators = resolveList(category, prominentOnly);
        return ApiResponse.success(translator.translate("api.macro.retrieved"),
                mapper.toResponses(indicators));
    }

    @GetMapping("/{code}/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MacroIndicatorPointResponse>> history(
            @PathVariable String code,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        MacroIndicator indicator = queryService.findByCode(code);
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null
                ? effectiveTo.minusYears(DEFAULT_HISTORY_YEARS) : from;
        return ApiResponse.success(translator.translate("api.macro.historyRetrieved"),
                mapper.toPointResponses(queryService.history(indicator, effectiveFrom, effectiveTo)));
    }

    private List<MacroIndicator> resolveList(MacroCategory category, boolean prominentOnly) {
        if (prominentOnly) {
            return queryService.listProminent();
        }
        return category != null ? queryService.listByCategory(category) : queryService.listAll();
    }
}
