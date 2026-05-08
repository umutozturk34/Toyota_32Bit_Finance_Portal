package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverviewDefaultsTest {

    private final OverviewDefaults defaults = new OverviewDefaults();

    @Test
    void should_provideEightSections_when_callingDefaultSections() {
        List<WidgetSection> sections = defaults.defaultSections();

        assertThat(sections).hasSize(8);
        assertThat(sections).extracting(WidgetSection::sectionId)
                .containsExactly("asset-cards-default", "movers-stock", "movers-crypto", "movers-forex",
                        "movers-fund", "movers-commodity", "watchlist-default", "news");
    }

    @Test
    void should_carryMarketInConfig_when_buildingMoverSection() {
        WidgetSection moversStock = defaults.defaultSections().stream()
                .filter(s -> "movers-stock".equals(s.sectionId()))
                .findFirst().orElseThrow();

        assertThat(moversStock.kind()).isEqualTo(WidgetKind.MOVERS);
        assertThat(moversStock.config().get("market").asText()).isEqualTo(MarketType.STOCK.name());
    }

    @Test
    void should_returnPositiveLimits_when_callingLimitGetters() {
        assertThat(defaults.defaultMoverLimit()).isPositive();
        assertThat(defaults.defaultNewsCount()).isPositive();
        assertThat(defaults.defaultWatchlistLimit()).isPositive();
    }

    @Test
    void should_includeAtLeastFiveAssetReferences_when_callingDefaults() {
        assertThat(defaults.defaultAssetReferences()).hasSizeGreaterThanOrEqualTo(5);
    }
}
