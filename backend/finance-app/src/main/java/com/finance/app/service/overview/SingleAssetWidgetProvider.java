package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import com.finance.app.dto.response.overview.SingleAssetData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class SingleAssetWidgetProvider implements OverviewWidgetProvider {

    private final Map<MarketType, MarketAssetProvider> providersByType;

    public SingleAssetWidgetProvider(List<MarketAssetProvider> providers) {
        this.providersByType = EnumDispatcher.from(MarketType.class, providers, MarketAssetProvider::getType);
    }

    @Override
    public WidgetKind kind() {
        return WidgetKind.SINGLE_ASSET;
    }

    @Override
    public SingleAssetData fetch(String userSub, WidgetSection section) {
        JsonNode config = section.config();
        String type = config.path("type").asString(null);
        String code = config.path("code").asString(null);
        if (type == null || code == null || type.isBlank() || code.isBlank()) {
            return new SingleAssetData(null);
        }
        MarketType marketType;
        try {
            marketType = MarketType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            log.debug("SingleAssetWidget skip — invalid type={}", type);
            return new SingleAssetData(null);
        }
        MarketAssetProvider provider = providersByType.get(marketType);
        if (provider == null) {
            log.debug("SingleAssetWidget skip — no provider for type={}", marketType);
            return new SingleAssetData(null);
        }
        try {
            MarketAssetResponse asset = provider.getByCode(code);
            return new SingleAssetData(asset);
        } catch (ResourceNotFoundException ex) {
            log.debug("SingleAssetWidget skip — code={} not found in {}", code, marketType);
            return new SingleAssetData(null);
        }
    }
}
