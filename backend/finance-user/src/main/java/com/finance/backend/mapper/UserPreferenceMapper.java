package com.finance.backend.mapper;

import com.finance.backend.dto.UserPreferenceResponse;
import com.finance.backend.model.UserPreference;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPreferenceMapper {

    UserPreferenceResponse toResponse(UserPreference entity);
}
