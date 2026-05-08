package com.finance.app.dto.response.overview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenderedWidgetTest {

    @Test
    void should_inheritKindFromData_when_creatingWithFactory() {
        AssetCardsData data = new AssetCardsData(List.of());

        RenderedWidget widget = RenderedWidget.of("asset-1", 0, data);

        assertThat(widget.sectionId()).isEqualTo("asset-1");
        assertThat(widget.order()).isZero();
        assertThat(widget.kind()).isEqualTo(WidgetKind.ASSET_CARDS);
        assertThat(widget.data()).isSameAs(data);
    }

    @Test
    void should_carryNullData_when_emptyFactoryCalled() {
        RenderedWidget widget = RenderedWidget.empty("news-broken", WidgetKind.NEWS, 7);

        assertThat(widget.sectionId()).isEqualTo("news-broken");
        assertThat(widget.order()).isEqualTo(7);
        assertThat(widget.kind()).isEqualTo(WidgetKind.NEWS);
        assertThat(widget.data()).isNull();
    }
}
