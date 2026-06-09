package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to create a watchlist with a bounded name. */
public record WatchlistCreateRequest(
        @NotBlank(message = "{validation.watchlist.name.required}")
        @Size(max = 25, message = "{validation.watchlist.name.maxLen}")
        String name
) {
}
