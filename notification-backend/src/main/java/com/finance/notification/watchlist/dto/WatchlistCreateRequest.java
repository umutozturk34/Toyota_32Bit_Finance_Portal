package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WatchlistCreateRequest(
        @NotBlank @Size(max = 64) String name
) {
}
