package com.finance.app.dto.response.overview;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WidgetDataKindTest {

    @Test
    void should_reportAssetCardsKind_when_payloadIsAssetCards() {
        WidgetData data = new AssetCardsData(List.of());

        assertThat(data.kind()).isEqualTo(WidgetKind.ASSET_CARDS);
    }

    @Test
    void should_reportMoversKind_when_payloadIsMovers() {
        WidgetData data = new MoverData(MarketType.STOCK, List.of(), List.of());

        assertThat(data.kind()).isEqualTo(WidgetKind.MOVERS);
    }

    @Test
    void should_reportWatchlistKind_when_payloadIsWatchlist() {
        WidgetData data = new WatchlistData(7L, "Favoriler", List.of());

        assertThat(data.kind()).isEqualTo(WidgetKind.WATCHLIST);
    }

    @Test
    void should_reportNewsKind_when_payloadIsNews() {
        WidgetData data = new NewsData(List.of(), List.of());

        assertThat(data.kind()).isEqualTo(WidgetKind.NEWS);
    }
}
