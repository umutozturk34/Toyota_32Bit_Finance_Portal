package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import com.finance.app.dto.response.overview.AssetCardsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the ASSET_CARDS widget: resolves the section's configured asset references (or the defaults) to
 * live market snapshots, capped at the configured max items. Unknown types and not-found codes are skipped
 * so a partial config still renders.
 */
@Log4j2
@Component
public class AssetCardsWidgetProvider implements OverviewWidgetProvider {

    private final Map<MarketType, MarketAssetProvider> providersByType;
    private final OverviewDefaults defaults;

    public AssetCardsWidgetProvider(List<MarketAssetProvider> providers, OverviewDefaults defaults) {
        this.providersByType = EnumDispatcher.from(MarketType.class, providers, MarketAssetProvider::getType);
        this.defaults = defaults;
    }

    @Override
    public WidgetKind kind() {
        return WidgetKind.ASSET_CARDS;
    }

    @Override
    public AssetCardsData fetch(String userSub, WidgetSection section) {
        List<AssetReference> requested = readReferences(section);
        if (requested == null) requested = defaults.defaultAssetReferences();
        int maxItems = defaults.maxAssetCardItems();
        List<MarketAssetResponse> resolved = new ArrayList<>(Math.min(requested.size(), maxItems));
        for (AssetReference ref : requested) {
            if (resolved.size() >= maxItems) break;
            resolveOne(ref).ifPresent(resolved::add);
        }
        return new AssetCardsData(resolved);
    }

    private Optional<MarketAssetResponse> resolveOne(AssetReference ref) {
        MarketAssetProvider provider = providersByType.get(ref.type());
        if (provider == null) {
            log.debug("AssetCardsWidget skip — no provider for type={}", ref.type());
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(provider.getByCode(ref.code()));
        } catch (ResourceNotFoundException ex) {
            log.debug("AssetCardsWidget skip — code={} not found in {}", ref.code(), ref.type());
            return Optional.empty();
        }
    }

    /**
     * Parses the configured asset references. Returns null when none are configured (caller substitutes
     * defaults) versus an empty list when explicitly configured empty; malformed entries are dropped.
     */
    private List<AssetReference> readReferences(WidgetSection section) {
        JsonNode node = section.config().get("assetCodes");
        if (node == null) return null;
        if (!node.isArray()) return List.of();
        List<AssetReference> refs = new ArrayList<>(node.size());
        for (JsonNode entry : node) {
            String type = entry.path("type").asString(null);
            String code = entry.path("code").asString(null);
            if (type == null || code == null || type.isBlank() || code.isBlank()) continue;
            try {
                refs.add(new AssetReference(MarketType.valueOf(type), code));
            } catch (IllegalArgumentException ex) {
                log.debug("AssetCardsWidget skip — invalid market type={}", type);
            }
        }
        return refs;
    }

    public record AssetReference(MarketType type, String code) {
    }
}
