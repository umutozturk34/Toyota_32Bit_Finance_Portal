package com.finance.user.mapper;

import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

/**
 * MapStruct mapper from the raw Keycloak user representation to the admin response DTO, normalizing
 * a null {@code enabled} to {@code false} and converting the epoch-millis timestamp to an {@link Instant}.
 * The app roles are supplied separately (already filtered by the caller) since they aren't part of the
 * user representation returned by the list endpoint.
 */
@Mapper(componentModel = "spring", imports = {Instant.class})
public interface KeycloakUserMapper {

    @Mapping(target = "enabled", expression = "java(source.enabled() != null && source.enabled())")
    @Mapping(target = "createdAt", expression = "java(source.createdTimestamp() != null ? Instant.ofEpochMilli(source.createdTimestamp()) : null)")
    @Mapping(target = "roles", source = "roles")
    AdminUserResponse toResponse(KeycloakUser source, List<String> roles);
}
