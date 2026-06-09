package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request to reorder a list's items; the ids must be the full set of that list's item ids. */
public record WatchlistReorderRequest(
        @NotEmpty(message = "{validation.watchlist.reorder.itemIds.required}")
        @Size(max = 200, message = "{validation.watchlist.reorder.itemIds.maxLen}")
        List<@NotNull Long> itemIds
) {
}
