package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request to reorder a list's items; the ids must be the full set of that list's item ids. */
public record WatchlistReorderRequest(
        @NotEmpty(message = "{validation.watchlist.reorder.itemIds.required}")
        List<@NotNull Long> itemIds
) {
}
