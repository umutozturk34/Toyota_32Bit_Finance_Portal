package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.model.MarketType;

import java.util.List;
import java.util.Map;

final class OverviewPropertiesFixture {

    private OverviewPropertiesFixture() {}

    static OverviewProperties standard() {
        return new OverviewProperties(
                new OverviewProperties.Limits(12, 4, 200, 60),
                new OverviewProperties.Defaults(
                        10, 10, 200,
                        List.of(
                                new OverviewProperties.AssetReferenceConfig(MarketType.STOCK, "XU100.IS"),
                                new OverviewProperties.AssetReferenceConfig(MarketType.STOCK, "XU030.IS"),
                                new OverviewProperties.AssetReferenceConfig(MarketType.FOREX, "USD"),
                                new OverviewProperties.AssetReferenceConfig(MarketType.FOREX, "EUR"),
                                new OverviewProperties.AssetReferenceConfig(MarketType.CRYPTO, "BTC"),
                                new OverviewProperties.AssetReferenceConfig(MarketType.COMMODITY, "XAUTRYG")
                        ),
                        List.of(
                                new OverviewProperties.DefaultSectionConfig("asset-cards-default", WidgetKind.ASSET_CARDS, 0, null),
                                new OverviewProperties.DefaultSectionConfig("news-default", WidgetKind.NEWS, 1, null),
                                new OverviewProperties.DefaultSectionConfig("movers-stock", WidgetKind.MOVERS, 2, MarketType.STOCK),
                                new OverviewProperties.DefaultSectionConfig("movers-crypto", WidgetKind.MOVERS, 3, MarketType.CRYPTO),
                                new OverviewProperties.DefaultSectionConfig("movers-forex", WidgetKind.MOVERS, 4, MarketType.FOREX),
                                new OverviewProperties.DefaultSectionConfig("movers-fund", WidgetKind.MOVERS, 5, MarketType.FUND),
                                new OverviewProperties.DefaultSectionConfig("movers-commodity", WidgetKind.MOVERS, 6, MarketType.COMMODITY)
                        )
                ),
                Map.of(
                        WidgetKind.ASSET_CARDS, new OverviewProperties.WidgetSettings(
                                new OverviewProperties.Size(8, 3),
                                new OverviewProperties.Size(3, 2),
                                new OverviewProperties.Size(12, 6),
                                12),
                        WidgetKind.MOVERS, new OverviewProperties.WidgetSettings(
                                new OverviewProperties.Size(4, 6),
                                new OverviewProperties.Size(3, 4),
                                new OverviewProperties.Size(6, 12),
                                null),
                        WidgetKind.WATCHLIST, new OverviewProperties.WidgetSettings(
                                new OverviewProperties.Size(4, 6),
                                new OverviewProperties.Size(3, 4),
                                new OverviewProperties.Size(6, 12),
                                null),
                        WidgetKind.NEWS, new OverviewProperties.WidgetSettings(
                                new OverviewProperties.Size(4, 14),
                                new OverviewProperties.Size(3, 6),
                                new OverviewProperties.Size(8, 24),
                                12)
                )
        );
    }
}
