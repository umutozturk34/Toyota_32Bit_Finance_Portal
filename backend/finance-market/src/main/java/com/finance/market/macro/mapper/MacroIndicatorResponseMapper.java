package com.finance.market.macro.mapper;

import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper exposing macro-indicator domain entities (the indicator and its time-series
 * points) as their API response DTOs. Mapping is field-by-field with no enrichment.
 */
@Mapper(componentModel = "spring")
public interface MacroIndicatorResponseMapper {

    /** Maps a macro indicator entity, including its nested point series, to its response DTO. */
    MacroIndicatorResponse toResponse(MacroIndicator indicator);

    /** Maps a list of indicator entities to response DTOs, preserving order. */
    List<MacroIndicatorResponse> toResponses(List<MacroIndicator> indicators);

    /** Maps a single time-series observation to its response DTO. */
    MacroIndicatorPointResponse toPointResponse(MacroIndicatorPoint point);

    /** Maps a list of time-series observations to response DTOs, preserving order. */
    List<MacroIndicatorPointResponse> toPointResponses(List<MacroIndicatorPoint> points);
}
