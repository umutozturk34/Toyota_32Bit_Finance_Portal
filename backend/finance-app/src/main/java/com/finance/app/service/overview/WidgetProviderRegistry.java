package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/**
 * Indexes the available {@link OverviewWidgetProvider}s by {@link WidgetKind} for lookup during rendering,
 * rejecting two providers claiming the same kind at startup to keep dispatch unambiguous.
 */
@Component
public class WidgetProviderRegistry {

    private final EnumMap<WidgetKind, OverviewWidgetProvider> byKind;

    public WidgetProviderRegistry(List<OverviewWidgetProvider> providers) {
        this.byKind = new EnumMap<>(WidgetKind.class);
        for (OverviewWidgetProvider provider : providers) {
            OverviewWidgetProvider previous = byKind.putIfAbsent(provider.kind(), provider);
            if (previous != null) {
                throw new BusinessException(
                        "Duplicate OverviewWidgetProvider for kind " + provider.kind(),
                        "OVERVIEW_PROVIDER_DUPLICATE");
            }
        }
    }

    public Optional<OverviewWidgetProvider> providerFor(WidgetKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }
}
