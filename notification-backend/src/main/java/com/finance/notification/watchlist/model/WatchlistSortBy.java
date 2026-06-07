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
    // assetCode is a @Transient projection of the tracked asset (filled in @PostLoad), NOT a persistent
    // column — declaring it DB-sortable made Sort.by("assetCode") throw PropertyReferenceException at query
    // time ("unexpected error" on alphabetical sort). It only exists after enrichment, so sort there instead,
    // case-insensitively (asset codes are ASCII tickers) with nulls last.
    NAME(null, Comparator.comparing(WatchlistItemResponse::assetCode,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))),
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

    /** True for sorts backed by an entity field (pushed into the query), false for post-enrich sorts. */
    public boolean isDbSortable() {
        return dbField != null;
    }

    /**
     * Builds the {@link Sort.Order} to push into the query for a DB-backed sort.
     *
     * @param direction the requested sort direction
     * @return an order on this sort's entity field
     * @throws IllegalStateException if this constant is not DB-sortable (a post-enrich sort)
     */
    public Sort.Order toDbOrder(Sort.Direction direction) {
        if (dbField == null) {
            throw new IllegalStateException(name() + " is not DB-sortable");
        }
        return new Sort.Order(direction, dbField);
    }

    /**
     * Returns the comparator applied to enriched (live-snapshot) responses for a non-DB sort,
     * reversed when the direction is descending.
     *
     * @param direction the requested sort direction
     * @return the direction-aware comparator over {@link WatchlistItemResponse}
     * @throws IllegalStateException if this constant has no post-enrich comparator (a DB-backed sort)
     */
    public Comparator<WatchlistItemResponse> postEnrichComparator(Sort.Direction direction) {
        return Optional.ofNullable(postEnrichComparator)
                .map(c -> direction == Sort.Direction.DESC ? c.reversed() : c)
                .orElseThrow(() -> new IllegalStateException(name() + " has no post-enrich comparator"));
    }
}
