package com.finance.notification.watchlist.model;

import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;

/**
 * Sort options for watchlist items. DB-backed sorts map to an entity field; price/change sorts have
 * no DB column and instead supply a comparator applied after items are enriched with live snapshot
 * data. Each constant is exactly one of the two.
 */
public enum WatchlistSortBy {
    CUSTOM("displayOrder", null),
    NAME("assetCode", null),
    LAST_SEEN_PRICE("lastSeenPrice", null),
    ADDED_AT("createdAt", null),
    CURRENT_PRICE(null, Comparator.comparing(WatchlistItemResponse::currentPrice,
            Comparator.nullsLast(BigDecimal::compareTo))),
    CHANGE_PERCENT(null, Comparator.comparing(WatchlistItemResponse::changePercent,
            Comparator.nullsLast(BigDecimal::compareTo)));

    private final String dbField;
    private final Comparator<WatchlistItemResponse> postEnrichComparator;

    WatchlistSortBy(String dbField, Comparator<WatchlistItemResponse> postEnrichComparator) {
        this.dbField = dbField;
        this.postEnrichComparator = postEnrichComparator;
    }

    public boolean isDbSortable() {
        return dbField != null;
    }

    public Sort.Order toDbOrder(Sort.Direction direction) {
        if (dbField == null) {
            throw new IllegalStateException(name() + " is not DB-sortable");
        }
        return new Sort.Order(direction, dbField);
    }

    public Comparator<WatchlistItemResponse> postEnrichComparator(Sort.Direction direction) {
        return Optional.ofNullable(postEnrichComparator)
                .map(c -> direction == Sort.Direction.DESC ? c.reversed() : c)
                .orElseThrow(() -> new IllegalStateException(name() + " has no post-enrich comparator"));
    }
}
