package com.finance.app.service.overview;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OverviewDefaults {

    private final OverviewProperties properties;

    public List<AssetCardsWidgetProvider.AssetReference> defaultAssetReferences() {
        List<OverviewProperties.AssetReferenceConfig> configured = properties.defaults().assetReferences();
        List<AssetCardsWidgetProvider.AssetReference> refs = new ArrayList<>(configured.size());
        for (OverviewProperties.AssetReferenceConfig c : configured) {
            refs.add(new AssetCardsWidgetProvider.AssetReference(c.type(), c.code()));
        }
        return refs;
    }

    public int defaultMoverLimit() {
        return properties.defaults().moverLimit();
    }

    public int defaultNewsCount() {
        return properties.defaults().newsCount();
    }

    public int defaultWatchlistLimit() {
        return properties.defaults().watchlistLimit();
    }

    public int maxConfigLimit() {
        return properties.limits().maxConfigLimit();
    }

    public int maxAssetCardItems() {
        return properties.settingsFor(com.finance.app.dto.response.overview.WidgetKind.ASSET_CARDS)
                .maxItems();
    }

    public int maxNewsItems() {
        return properties.settingsFor(com.finance.app.dto.response.overview.WidgetKind.NEWS)
                .maxItems();
    }

    public int maxAssetCardWidgetsPerLayout() {
        return properties.limits().maxAssetCardWidgetsPerLayout();
    }

    public List<WidgetSection> defaultSections() {
        List<OverviewProperties.DefaultSectionConfig> configured = properties.defaults().sections();
        List<WidgetSection> sections = new ArrayList<>(configured.size());
        for (OverviewProperties.DefaultSectionConfig c : configured) {
            sections.add(new WidgetSection(c.sectionId(), c.kind(), c.order(), buildConfigNode(c)));
        }
        return sections;
    }

    private ObjectNode buildConfigNode(OverviewProperties.DefaultSectionConfig c) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (c.marketType() != null) {
            node.put("market", c.marketType().name());
        }
        return node;
    }
}
