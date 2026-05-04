package com.finance.backend.mapper;

import com.finance.backend.dto.AdminUserResponse;
import com.finance.backend.dto.KeycloakUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = {Instant.class})
public interface KeycloakUserMapper {

    @Mapping(target = "enabled", expression = "java(source.enabled() != null && source.enabled())")
    @Mapping(target = "createdAt", expression = "java(source.createdTimestamp() != null ? Instant.ofEpochMilli(source.createdTimestamp()) : null)")
    AdminUserResponse toResponse(KeycloakUser source);
}
