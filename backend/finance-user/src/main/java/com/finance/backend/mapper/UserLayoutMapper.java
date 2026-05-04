package com.finance.backend.mapper;

import com.finance.backend.dto.UserLayoutResponse;
import com.finance.backend.model.UserLayout;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserLayoutMapper {

    UserLayoutResponse toResponse(UserLayout entity);
}
