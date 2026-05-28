package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.WidgetData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;

/**
 * Strategy for producing one overview widget's data. Each implementation declares the {@link WidgetKind} it
 * serves (used by {@link WidgetProviderRegistry} for dispatch) and fetches data for a given user and section
 * config.
 */
public interface OverviewWidgetProvider {

    WidgetKind kind();

    WidgetData fetch(String userSub, WidgetSection section);
}
