package com.finance.notification.watchlist.model;

import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistSortByTest {

    private WatchlistItemResponse item(BigDecimal currentPrice, BigDecimal changePercent) {
        return new WatchlistItemResponse(1L, MarketType.STOCK, "X", "X", null,
                currentPrice, null, changePercent, null, null, null, null, null);
    }

    @Test
    void isDbSortable_returnsTrue_forDbBackedConstants() {
        assertThat(WatchlistSortBy.CUSTOM.isDbSortable()).isTrue();
        assertThat(WatchlistSortBy.NAME.isDbSortable()).isTrue();
        assertThat(WatchlistSortBy.LAST_SEEN_PRICE.isDbSortable()).isTrue();
        assertThat(WatchlistSortBy.ADDED_AT.isDbSortable()).isTrue();
    }

    @Test
    void isDbSortable_returnsFalse_forPostEnrichOnlyConstants() {
        assertThat(WatchlistSortBy.CURRENT_PRICE.isDbSortable()).isFalse();
        assertThat(WatchlistSortBy.CHANGE_PERCENT.isDbSortable()).isFalse();
    }

    @Test
    void toDbOrder_buildsOrder_withRequestedDirection() {
        Sort.Order asc = WatchlistSortBy.CUSTOM.toDbOrder(Sort.Direction.ASC);

        assertThat(asc.getProperty()).isEqualTo("displayOrder");
        assertThat(asc.isAscending()).isTrue();
    }

    @Test
    void toDbOrder_raises_whenCalledOnPostEnrichOnlySortKey() {
        assertThatThrownBy(() -> WatchlistSortBy.CURRENT_PRICE.toDbOrder(Sort.Direction.ASC))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void postEnrichComparator_sortsAscendingByCurrentPrice() {
        Comparator<WatchlistItemResponse> comparator =
                WatchlistSortBy.CURRENT_PRICE.postEnrichComparator(Sort.Direction.ASC);
        List<WatchlistItemResponse> sorted = Stream.of(
                item(new BigDecimal("10"), null),
                item(new BigDecimal("5"), null),
                item(new BigDecimal("20"), null)).sorted(comparator).toList();

        assertThat(sorted).extracting(WatchlistItemResponse::currentPrice)
                .containsExactly(new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("20"));
    }

    @Test
    void postEnrichComparator_reversesForDescending() {
        Comparator<WatchlistItemResponse> comparator =
                WatchlistSortBy.CHANGE_PERCENT.postEnrichComparator(Sort.Direction.DESC);
        List<WatchlistItemResponse> sorted = Stream.of(
                item(null, new BigDecimal("1")),
                item(null, new BigDecimal("3")),
                item(null, new BigDecimal("2"))).sorted(comparator).toList();

        assertThat(sorted).extracting(WatchlistItemResponse::changePercent)
                .containsExactly(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("1"));
    }

    @Test
    void postEnrichComparator_raises_whenCalledOnDbOnlySortKey() {
        assertThatThrownBy(() -> WatchlistSortBy.CUSTOM.postEnrichComparator(Sort.Direction.ASC))
                .isInstanceOf(IllegalStateException.class);
    }
}
