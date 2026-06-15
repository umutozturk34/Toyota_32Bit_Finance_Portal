package com.finance.notification.core.dispatch.payload;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadMetadataTest {

    @Test
    void priceAlertPayload_toMetadata_includesAllRequiredFields() {
        PriceAlertPayload payload = new PriceAlertPayload(1L, MarketType.STOCK, "AAPL",
                AlertDirection.ABOVE, new BigDecimal("110"), new BigDecimal("105"),
                "img", "Apple", "USD");

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).containsEntry("alertId", 1L)
                .containsEntry("marketType", "STOCK")
                .containsEntry("assetCode", "AAPL")
                .containsEntry("direction", "ABOVE")
                .containsEntry("image", "img")
                .containsEntry("assetName", "Apple")
                .containsEntry("currency", "USD");
        assertThat(payload.type()).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
    }

    @Test
    void priceAlertPayload_omitsOptionals_whenNull() {
        PriceAlertPayload payload = new PriceAlertPayload(1L, MarketType.STOCK, "AAPL",
                AlertDirection.BELOW, new BigDecimal("110"), new BigDecimal("105"),
                null, null, null);

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).doesNotContainKeys("image", "assetName", "currency");
    }

    @Test
    void watchlistDeltaPayload_toMetadata_includesItemsAsList() {
        WatchlistDeltaPayload.DeltaItem item = new WatchlistDeltaPayload.DeltaItem(
                1L, "AAPL", "Apple", "img",
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("10"));
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                7L, "Favs", true, MarketType.STOCK, List.of(item));

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).containsEntry("watchlistId", 7L)
                .containsEntry("watchlistName", "Favs")
                .containsEntry("defaultList", true)
                .containsEntry("marketType", "STOCK");
        assertThat((List<?>) meta.get("items")).hasSize(1);
        assertThat(payload.type()).isEqualTo(NotificationType.WATCHLIST_DELTA);
    }

    @Test
    void watchlistDeltaPayload_omitsName_whenNull() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                7L, null, false, MarketType.CRYPTO, List.of());

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).doesNotContainKey("watchlistName");
    }

    @Test
    void watchlistDeltaItem_toMap_omitsNullableFields_whenAbsent() {
        WatchlistDeltaPayload.DeltaItem item = new WatchlistDeltaPayload.DeltaItem(
                1L, "AAPL", null, null,
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("10"));

        Map<String, Object> map = item.toMap();

        assertThat(map).doesNotContainKeys("assetName", "image");
        assertThat(map).containsEntry("assetCode", "AAPL")
                .containsEntry("lastSeenPrice", new BigDecimal("100"));
    }

    @Test
    void newsPublishedPayload_toMetadata_includesPopulatedLists() {
        NewsPublishedPayload payload = new NewsPublishedPayload(5,
                List.of("STOCK"), List.of("BIST yükseldi"), "scheduler");

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).containsEntry("articleCount", 5)
                .containsEntry("categories", List.of("STOCK"))
                .containsEntry("sampleTitles", List.of("BIST yükseldi"))
                .containsEntry("source", "scheduler");
        assertThat(payload.type()).isEqualTo(NotificationType.NEWS_PUBLISHED);
    }

    @Test
    void newsPublishedPayload_omitsEmptyLists_andNullSource() {
        NewsPublishedPayload payload = new NewsPublishedPayload(3, List.of(), List.of(), null);

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).doesNotContainKeys("categories", "sampleTitles", "source");
    }

    @Test
    void newsPublishedPayload_omitsNullLists() {
        NewsPublishedPayload payload = new NewsPublishedPayload(3, null, null, null);

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).doesNotContainKeys("categories", "sampleTitles");
    }

    @Test
    void portfolioUpdatedPayload_toMetadata_includesPnlFields_whenPresent() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("1000"), new BigDecimal("10"), new BigDecimal("1.0"),
                2, List.of(new PortfolioUpdatedPayload.Line(
                        7L, "Portföyüm", "FIXED", new BigDecimal("600"), new BigDecimal("6"), new BigDecimal("1.0"))),
                "scheduler");

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).containsEntry("totalValue", new BigDecimal("1000"))
                .containsEntry("dailyPnl", new BigDecimal("10"))
                .containsEntry("dailyPnlPercent", new BigDecimal("1.0"))
                .containsEntry("portfolioCount", 2);
        assertThat(payload.type()).isEqualTo(NotificationType.PORTFOLIO_UPDATED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) meta.get("portfolios");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "Portföyüm").containsEntry("id", 7L);
    }

    @Test
    void portfolioUpdatedPayload_omitsNullPnlFields() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(null, null, null, 0, null, null);

        Map<String, Object> meta = payload.toMetadata();

        assertThat(meta).doesNotContainKeys("totalValue", "dailyPnl", "dailyPnlPercent", "portfolios");
        assertThat(meta).containsEntry("portfolioCount", 0);
    }
}
