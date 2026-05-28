package com.finance.user.mapper;

import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.model.UserPreference;
import org.mapstruct.Mapper;

/** MapStruct mapper from {@link UserPreference} entity to its API response DTO. */
@Mapper(componentModel = "spring")
public interface UserPreferenceMapper {

    UserPreferenceResponse toResponse(UserPreference entity);
}
