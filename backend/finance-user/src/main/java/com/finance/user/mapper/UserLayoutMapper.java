package com.finance.user.mapper;

import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.model.UserLayout;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserLayoutMapper {

    UserLayoutResponse toResponse(UserLayout entity);
}
