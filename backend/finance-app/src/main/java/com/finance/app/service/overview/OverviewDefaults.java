package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.model.MarketType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OverviewDefaults {

    public static final int MAX_CONFIG_LIMIT = 20;
    public static final int MAX_ASSET_CARDS = 12;
    public static final int MAX_NEWS_COUNT = 12;

    private static final List<AssetCardsWidgetProvider.AssetReference> DEFAULT_ASSETS = List.of(
            new AssetCardsWidgetProvider.AssetReference(MarketType.STOCK, "XU100.IS"),
            new AssetCardsWidgetProvider.AssetReference(MarketType.STOCK, "XU030.IS"),
            new AssetCardsWidgetProvider.AssetReference(MarketType.FOREX, "USDTRY"),
            new AssetCardsWidgetProvider.AssetReference(MarketType.FOREX, "EURTRY"),
            new AssetCardsWidgetProvider.AssetReference(MarketType.CRYPTO, "BTC"),
            new AssetCardsWidgetProvider.AssetReference(MarketType.COMMODITY, "XAUTRYG")
    );

    private static final int DEFAULT_MOVER_LIMIT = 5;
    private static final int DEFAULT_NEWS_COUNT = 6;
    private static final int DEFAULT_WATCHLIST_LIMIT = 8;

    public List<AssetCardsWidgetProvider.AssetReference> defaultAssetReferences() {
        return DEFAULT_ASSETS;
    }

    public int defaultMoverLimit() {
        return DEFAULT_MOVER_LIMIT;
    }

    public int defaultNewsCount() {
        return DEFAULT_NEWS_COUNT;
    }

    public int defaultWatchlistLimit() {
        return DEFAULT_WATCHLIST_LIMIT;
    }

    public List<WidgetSection> defaultSections() {
        return List.of(
                new WidgetSection("asset-cards-default", WidgetKind.ASSET_CARDS, 0, JsonNodeFactory.instance.objectNode()),
                new WidgetSection("movers-stock", WidgetKind.MOVERS, 1, marketConfig(MarketType.STOCK)),
                new WidgetSection("movers-crypto", WidgetKind.MOVERS, 2, marketConfig(MarketType.CRYPTO)),
                new WidgetSection("movers-forex", WidgetKind.MOVERS, 3, marketConfig(MarketType.FOREX)),
                new WidgetSection("movers-fund", WidgetKind.MOVERS, 4, marketConfig(MarketType.FUND)),
                new WidgetSection("movers-commodity", WidgetKind.MOVERS, 5, marketConfig(MarketType.COMMODITY)),
                new WidgetSection("watchlist-default", WidgetKind.WATCHLIST, 6, JsonNodeFactory.instance.objectNode()),
                new WidgetSection("news", WidgetKind.NEWS, 7, JsonNodeFactory.instance.objectNode()));
    }

    private static ObjectNode marketConfig(MarketType market) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("market", market.name());
        return node;
    }
}
