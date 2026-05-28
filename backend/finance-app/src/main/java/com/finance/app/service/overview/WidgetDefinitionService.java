package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse.Limits;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse.Size;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse.WidgetDefinition;
import com.finance.app.dto.response.overview.WidgetKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the catalog of widget definitions (size defaults/min/max and max items per kind, plus layout
 * limits) that the client uses to render and validate the overview customization UI.
 */
@Service
@RequiredArgsConstructor
public class WidgetDefinitionService {

    private final OverviewProperties properties;

    public WidgetDefinitionResponse build() {
        List<WidgetDefinition> defs = new ArrayList<>(WidgetKind.values().length);
        for (WidgetKind kind : WidgetKind.values()) {
            OverviewProperties.WidgetSettings s = properties.settingsFor(kind);
            defs.add(new WidgetDefinition(
                    kind,
                    new Size(s.defaults().w(), s.defaults().h()),
                    new Size(s.min().w(), s.min().h()),
                    new Size(s.max().w(), s.max().h()),
                    s.maxItems()
            ));
        }
        Limits limits = new Limits(
                properties.limits().maxWidgetsPerLayout(),
                properties.limits().maxAssetCardWidgetsPerLayout(),
                properties.limits().maxConfigLimit(),
                properties.limits().maxLayoutRows()
        );
        return new WidgetDefinitionResponse(defs, limits);
    }
}
