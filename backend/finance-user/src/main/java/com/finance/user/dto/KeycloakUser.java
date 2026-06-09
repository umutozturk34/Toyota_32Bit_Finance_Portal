package com.finance.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Deserialized Keycloak Admin REST user representation; unknown fields are ignored to tolerate API drift. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        Long createdTimestamp
) {
}
