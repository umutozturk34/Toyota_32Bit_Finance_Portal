package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.finance.market.macro.service.MacroIndicatorResponseAssembler;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Authenticated read API for macro indicators: list (optionally by category or prominent-only) and fetch an
 * indicator's history by code. History defaults to a configured number of trailing years when no range given.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/macro-indicators")
@RequiredArgsConstructor
@Validated
public class MacroIndicatorController {

    private final MacroIndicatorQueryService queryService;
    private final MacroIndicatorResponseAssembler assembler;
    private final Translator translator;
    private final MacroProperties macroProperties;

    /**
     * Lists macro indicators; narrowed to a single {@code category}, or to the prominent set when
     * {@code prominentOnly} is true (which takes precedence over {@code category}).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MacroIndicatorResponse>> list(
            @RequestParam(required = false) MacroCategory category,
            @RequestParam(required = false, defaultValue = "false") boolean prominentOnly) {
        List<MacroIndicator> indicators = resolveList(category, prominentOnly);
        return ApiResponse.success(translator.translate("api.macro.retrieved"),
                assembler.toResponses(indicators));
    }

    /**
     * Returns the point history for the indicator identified by {@code code}. An omitted {@code to}
     * defaults to today, and an omitted {@code from} to the configured trailing-years window before it.
     */
    @GetMapping("/{code}/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MacroIndicatorPointResponse>> history(
            @PathVariable @Size(max = 64) String code,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        MacroIndicator indicator = queryService.findByPublicId(code);
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null
                ? effectiveTo.minusYears(macroProperties.defaultHistoryYears()) : from;
        return ApiResponse.success(translator.translate("api.macro.historyRetrieved"),
                assembler.toPointResponses(queryService.history(indicator, effectiveFrom, effectiveTo)));
    }

    private List<MacroIndicator> resolveList(MacroCategory category, boolean prominentOnly) {
        if (prominentOnly) {
            return queryService.listProminent();
        }
        return category != null ? queryService.listByCategory(category) : queryService.listAll();
    }
}
