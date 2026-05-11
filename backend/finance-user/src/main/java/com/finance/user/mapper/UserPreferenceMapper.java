package com.finance.user.mapper;

import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.model.UserPreference;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPreferenceMapper {

    UserPreferenceResponse toResponse(UserPreference entity);
}
