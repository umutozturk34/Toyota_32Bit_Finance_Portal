package com.finance.user.client.dto;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") int expiresInSeconds,
        @JsonProperty("token_type") String tokenType
) {
}
