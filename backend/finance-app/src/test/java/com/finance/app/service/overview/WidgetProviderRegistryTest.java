package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.WidgetData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WidgetProviderRegistryTest {

    private static class StubProvider implements OverviewWidgetProvider {
        private final WidgetKind kind;

        StubProvider(WidgetKind kind) {
            this.kind = kind;
        }

        @Override
        public WidgetKind kind() {
            return kind;
        }

        @Override
        public WidgetData fetch(String userSub, WidgetSection section) {
            return null;
        }
    }

    @Test
    void should_registerEachProviderUnderItsKind_when_constructedWithUniqueProviders() {
        OverviewWidgetProvider movers = new StubProvider(WidgetKind.MOVERS);
        OverviewWidgetProvider news = new StubProvider(WidgetKind.NEWS);

        WidgetProviderRegistry registry = new WidgetProviderRegistry(List.of(movers, news));

        Optional<OverviewWidgetProvider> moversFor = registry.providerFor(WidgetKind.MOVERS);
        Optional<OverviewWidgetProvider> newsFor = registry.providerFor(WidgetKind.NEWS);

        assertThat(moversFor).contains(movers);
        assertThat(newsFor).contains(news);
    }

    @Test
    void should_returnEmpty_when_kindHasNoRegisteredProvider() {
        WidgetProviderRegistry registry = new WidgetProviderRegistry(List.of(new StubProvider(WidgetKind.MOVERS)));

        Optional<OverviewWidgetProvider> resolved = registry.providerFor(WidgetKind.WATCHLIST);

        assertThat(resolved).isEmpty();
    }

    @Test
    void should_throwBusinessException_when_twoProvidersClaimSameKind() {
        OverviewWidgetProvider first = new StubProvider(WidgetKind.MOVERS);
        OverviewWidgetProvider duplicate = new StubProvider(WidgetKind.MOVERS);

        assertThatThrownBy(() -> new WidgetProviderRegistry(List.of(first, duplicate)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("MOVERS");
    }

    @Test
    void should_returnEmpty_when_constructedWithNoProviders() {
        WidgetProviderRegistry registry = new WidgetProviderRegistry(List.of());

        for (WidgetKind kind : WidgetKind.values()) {
            Optional<OverviewWidgetProvider> resolved = registry.providerFor(kind);

            assertThat(resolved).isEmpty();
        }
    }
}
