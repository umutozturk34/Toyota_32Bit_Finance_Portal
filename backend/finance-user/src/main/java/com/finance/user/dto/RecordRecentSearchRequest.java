package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to record a user's selection of a search result so it appears in their recent searches.
 * The {@code name} is optional (display only); {@code code} and {@code type} identify the asset.
 */
public record RecordRecentSearchRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 32) String type,
        @Size(max = 255) String name
) {
}
