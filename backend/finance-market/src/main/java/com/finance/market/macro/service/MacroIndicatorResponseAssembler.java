package com.finance.market.macro.service;

import com.finance.market.macro.dto.InflationRate;
import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.mapper.MacroIndicatorResponseMapper;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles macro-indicator response DTOs, enriching index-based inflation indicators with their derived
 * year-over-year and month-over-month rates ({@link MacroInflationRateService}). Plain field mapping stays
 * in {@link MacroIndicatorResponseMapper}; this layer adds the figures the mapper cannot derive from the entity.
 */
@Component
@RequiredArgsConstructor
public class MacroIndicatorResponseAssembler {

    private final MacroIndicatorResponseMapper mapper;
    private final MacroInflationRateService inflationRateService;

    /** Maps indicators to responses, attaching derived inflation rates where applicable, preserving order. */
    public List<MacroIndicatorResponse> toResponses(List<MacroIndicator> indicators) {
        return indicators.stream().map(this::toResponse).toList();
    }

    /** Maps a single indicator, attaching its derived inflation rates (both {@code null} for non-inflation series). */
    public MacroIndicatorResponse toResponse(MacroIndicator indicator) {
        InflationRate rate = inflationRateService.compute(indicator);
        return mapper.toResponse(indicator).withInflationRates(rate.yoyChangePct(), rate.momChangePct());
    }

    /** Maps time-series observations to response DTOs, preserving order. */
    public List<MacroIndicatorPointResponse> toPointResponses(List<MacroIndicatorPoint> points) {
        return mapper.toPointResponses(points);
    }
}
