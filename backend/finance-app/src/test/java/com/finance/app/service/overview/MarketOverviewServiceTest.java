package com.finance.app.service.overview;

import tools.jackson.databind.node.JsonNodeFactory;
import com.finance.app.dto.response.overview.AssetCardsData;
import com.finance.app.dto.response.overview.NewsData;
import com.finance.app.dto.response.overview.RenderedWidget;
import com.finance.app.dto.response.overview.WidgetData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketOverviewServiceTest {

    private OverviewLayoutReader layoutReader;
    private WidgetProviderRegistry registry;
    private MarketOverviewService service;

    @BeforeEach
    void setUp() {
        layoutReader = mock(OverviewLayoutReader.class);
        registry = mock(WidgetProviderRegistry.class);
        service = new MarketOverviewService(layoutReader, registry);
    }

    private WidgetSection section(String id, WidgetKind kind, int order) {
        return new WidgetSection(id, kind, order, JsonNodeFactory.instance.objectNode());
    }

    private OverviewWidgetProvider providerThatReturns(WidgetKind kind, WidgetData data) {
        OverviewWidgetProvider provider = mock(OverviewWidgetProvider.class);
        when(provider.kind()).thenReturn(kind);
        when(provider.fetch(any(), any())).thenReturn(data);
        return provider;
    }

    @Test
    void should_renderEachSectionViaProvider_when_layoutHasMultiple() {
        WidgetSection cards = section("asset-cards", WidgetKind.ASSET_CARDS, 0);
        WidgetSection news = section("news", WidgetKind.NEWS, 1);
        AssetCardsData cardsData = new AssetCardsData(List.of());
        NewsData newsData = new NewsData(List.of(), List.of());
        OverviewWidgetProvider cardsProvider = providerThatReturns(WidgetKind.ASSET_CARDS, cardsData);
        OverviewWidgetProvider newsProvider = providerThatReturns(WidgetKind.NEWS, newsData);
        when(layoutReader.readVisibleSections("user-1", null)).thenReturn(List.of(cards, news));
        when(registry.providerFor(WidgetKind.ASSET_CARDS)).thenReturn(Optional.of(cardsProvider));
        when(registry.providerFor(WidgetKind.NEWS)).thenReturn(Optional.of(newsProvider));

        List<RenderedWidget> rendered = service.render("user-1");

        assertThat(rendered).hasSize(2);
        assertThat(rendered.get(0).sectionId()).isEqualTo("asset-cards");
        assertThat(rendered.get(0).data()).isSameAs(cardsData);
        assertThat(rendered.get(1).sectionId()).isEqualTo("news");
        assertThat(rendered.get(1).data()).isSameAs(newsData);
    }

    @Test
    void should_emitEmptyRenderedWidget_when_kindHasNoProvider() {
        WidgetSection orphan = section("ghost", WidgetKind.WATCHLIST, 0);
        when(layoutReader.readVisibleSections("user-1", null)).thenReturn(List.of(orphan));
        when(registry.providerFor(WidgetKind.WATCHLIST)).thenReturn(Optional.empty());

        List<RenderedWidget> rendered = service.render("user-1");

        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).data()).isNull();
        assertThat(rendered.get(0).sectionId()).isEqualTo("ghost");
    }

    @Test
    void should_emitEmptyRenderedWidget_when_providerThrowsDownstreamIOFailure() {
        WidgetSection unstable = section("movers-stock", WidgetKind.MOVERS, 0);
        OverviewWidgetProvider provider = mock(OverviewWidgetProvider.class);
        when(provider.kind()).thenReturn(WidgetKind.MOVERS);
        when(provider.fetch(any(), any())).thenThrow(new DataAccessResourceFailureException("redis down"));
        when(layoutReader.readVisibleSections("user-1", null)).thenReturn(List.of(unstable));
        when(registry.providerFor(WidgetKind.MOVERS)).thenReturn(Optional.of(provider));

        List<RenderedWidget> rendered = service.render("user-1");

        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).data()).isNull();
    }

    @Test
    void should_propagateBusinessException_when_providerSignalsDomainError() {
        WidgetSection any = section("movers-stock", WidgetKind.MOVERS, 0);
        OverviewWidgetProvider provider = mock(OverviewWidgetProvider.class);
        when(provider.kind()).thenReturn(WidgetKind.MOVERS);
        when(provider.fetch(any(), any())).thenThrow(new BusinessException("invalid"));
        when(layoutReader.readVisibleSections("user-1", null)).thenReturn(List.of(any));
        when(registry.providerFor(WidgetKind.MOVERS)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service.render("user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void should_returnEmptyList_when_layoutHasNoSections() {
        when(layoutReader.readVisibleSections("user-1", null)).thenReturn(List.of());

        List<RenderedWidget> rendered = service.render("user-1");

        assertThat(rendered).isEmpty();
    }
}
