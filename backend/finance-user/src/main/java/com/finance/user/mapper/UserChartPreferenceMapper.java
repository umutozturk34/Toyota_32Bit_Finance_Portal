package com.finance.user.mapper;

import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.model.UserChartPreference;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserChartPreferenceMapper {
    UserChartPreferenceResponse toResponse(UserChartPreference entity);
}
