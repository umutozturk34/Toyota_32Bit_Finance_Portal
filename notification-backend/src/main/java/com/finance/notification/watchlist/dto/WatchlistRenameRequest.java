package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WatchlistRenameRequest(
        @NotBlank(message = "{validation.watchlist.name.required}")
        @Size(max = 64, message = "{validation.watchlist.name.maxLen}")
        String name
) {
}
