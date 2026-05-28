package com.finance.user.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Keycloak OAuth2 token-endpoint response for the admin client-credentials grant. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") int expiresInSeconds,
        @JsonProperty("token_type") String tokenType
) {
}
