package com.finance.user.mapper;

import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.model.UserChartDrawing;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserChartDrawingMapper {
    UserChartDrawingResponse toResponse(UserChartDrawing entity);
}
