package com.finance.market.core.util;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsResponseMergerTest {

    @Test
    void should_combineSeriesColumnsPerDate_when_chunksCoverDisjointCodes() {
        // Arrange — two chunks fetched for disjoint series over the same two dates
        EvdsDataResponse usd = new EvdsDataResponse(2, List.of(
                Map.of("Tarih", "01-06-2026", "TP_DK_USD_A_YTL", "32.10"),
                Map.of("Tarih", "02-06-2026", "TP_DK_USD_A_YTL", "32.20")));
        EvdsDataResponse eur = new EvdsDataResponse(2, List.of(
                Map.of("Tarih", "01-06-2026", "TP_DK_EUR_A_YTL", "34.50"),
                Map.of("Tarih", "02-06-2026", "TP_DK_EUR_A_YTL", "34.60")));

        // Act
        EvdsDataResponse merged = EvdsResponseMerger.mergeByDate(List.of(usd, eur));

        // Assert — each date now carries both currencies' columns
        assertThat(merged.items()).hasSize(2);
        assertThat(merged.items().get(0))
                .containsEntry("Tarih", "01-06-2026")
                .containsEntry("TP_DK_USD_A_YTL", "32.10")
                .containsEntry("TP_DK_EUR_A_YTL", "34.50");
        assertThat(merged.items().get(1))
                .containsEntry("TP_DK_USD_A_YTL", "32.20")
                .containsEntry("TP_DK_EUR_A_YTL", "34.60");
        assertThat(merged.totalCount()).isEqualTo(4);
    }

    @Test
    void should_orderRowsByAscendingDate_when_chunksArriveOutOfOrder() {
        // Arrange — a chunk whose only date is newer than another chunk's, supplied first
        EvdsDataResponse newer = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "10-06-2026", "TP_DK_USD_A_YTL", "33.00")));
        EvdsDataResponse older = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "02-06-2026", "TP_DK_EUR_A_YTL", "34.00")));

        // Act
        EvdsDataResponse merged = EvdsResponseMerger.mergeByDate(List.of(newer, older));

        // Assert — merged rows are date-ascending (dd-MM-yyyy parsed, not string-sorted)
        assertThat(merged.items()).hasSize(2);
        assertThat(merged.items().get(0)).containsEntry("Tarih", "02-06-2026");
        assertThat(merged.items().get(1)).containsEntry("Tarih", "10-06-2026");
    }

    @Test
    void should_dropDatelessRowsAndTolerateNulls_when_merging() {
        // Arrange — a null part, a null item list, and a dateless row mixed in
        EvdsDataResponse withDateless = new EvdsDataResponse(3, java.util.Arrays.asList(
                Map.of("Tarih", "01-06-2026", "TP_DK_USD_A_YTL", "32.10"),
                Map.of("TP_DK_USD_A_YTL", "9.99"))); // no Tarih -> unusable downstream
        EvdsDataResponse emptyItems = new EvdsDataResponse(0, null);

        // Act
        EvdsDataResponse merged = EvdsResponseMerger.mergeByDate(
                java.util.Arrays.asList(withDateless, null, emptyItems));

        // Assert — only the dated row survives; counts still sum
        assertThat(merged.items()).hasSize(1);
        assertThat(merged.items().getFirst()).containsEntry("Tarih", "01-06-2026");
        assertThat(merged.totalCount()).isEqualTo(3);
    }
}
