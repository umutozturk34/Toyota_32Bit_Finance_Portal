package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WatchlistReorderRequest(
        @NotEmpty(message = "{validation.watchlist.reorder.itemIds.required}")
        List<@NotNull Long> itemIds
) {
}
