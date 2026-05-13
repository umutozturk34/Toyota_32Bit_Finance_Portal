package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse;
import com.finance.app.dto.response.overview.WidgetKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WidgetDefinitionServiceTest {

    private WidgetDefinitionService service;

    @BeforeEach
    void setUp() {
        OverviewProperties.Limits limits = new OverviewProperties.Limits(10, 3, 50, 8);
        OverviewProperties.WidgetSettings settings = new OverviewProperties.WidgetSettings(
                new OverviewProperties.Size(4, 3),
                new OverviewProperties.Size(2, 2),
                new OverviewProperties.Size(6, 5),
                12);
        Map<WidgetKind, OverviewProperties.WidgetSettings> map = new EnumMap<>(WidgetKind.class);
        for (WidgetKind kind : WidgetKind.values()) {
            map.put(kind, settings);
        }
        OverviewProperties props = new OverviewProperties(limits, null, map);
        service = new WidgetDefinitionService(props);
    }

    @Test
    void build_returnsEntryPerWidgetKind() {
        WidgetDefinitionResponse response = service.build();

        assertThat(response.widgets()).hasSize(WidgetKind.values().length);
    }

    @Test
    void build_populatesSizeBlocks_fromProperties() {
        WidgetDefinitionResponse response = service.build();

        WidgetDefinitionResponse.WidgetDefinition first = response.widgets().get(0);
        assertThat(first.defaults().w()).isEqualTo(4);
        assertThat(first.defaults().h()).isEqualTo(3);
        assertThat(first.min().w()).isEqualTo(2);
        assertThat(first.max().h()).isEqualTo(5);
        assertThat(first.maxItems()).isEqualTo(12);
    }

    @Test
    void build_populatesLimits_fromProperties() {
        WidgetDefinitionResponse response = service.build();

        assertThat(response.limits().maxWidgetsPerLayout()).isEqualTo(10);
        assertThat(response.limits().maxAssetCardWidgetsPerLayout()).isEqualTo(3);
        assertThat(response.limits().maxConfigLimit()).isEqualTo(50);
        assertThat(response.limits().maxLayoutRows()).isEqualTo(8);
    }
}
