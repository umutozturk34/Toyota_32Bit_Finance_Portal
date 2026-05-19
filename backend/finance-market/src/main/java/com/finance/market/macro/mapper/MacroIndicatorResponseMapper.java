package com.finance.market.macro.mapper;

import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MacroIndicatorResponseMapper {

    MacroIndicatorResponse toResponse(MacroIndicator indicator);

    List<MacroIndicatorResponse> toResponses(List<MacroIndicator> indicators);

    MacroIndicatorPointResponse toPointResponse(MacroIndicatorPoint point);

    List<MacroIndicatorPointResponse> toPointResponses(List<MacroIndicatorPoint> points);
}
