package com.finance.user.mapper;

import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.model.UserLayout;
import org.mapstruct.Mapper;

/** MapStruct mapper from {@link UserLayout} entity to its API response DTO. */
@Mapper(componentModel = "spring")
public interface UserLayoutMapper {
    UserLayoutResponse toResponse(UserLayout entity);
}
