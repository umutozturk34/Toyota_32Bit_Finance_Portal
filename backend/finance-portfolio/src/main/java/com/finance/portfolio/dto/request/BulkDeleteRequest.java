package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for deleting several positions (spot or derivative) in one call, so the client sends one
 * request instead of one DELETE per row. Capped to keep a single transaction bounded.
 */
public record BulkDeleteRequest(
        @NotEmpty @Size(max = 500) List<@NotNull Long> ids
) {
}
