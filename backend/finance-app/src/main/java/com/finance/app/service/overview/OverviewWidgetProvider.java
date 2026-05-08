package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.WidgetData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;

public interface OverviewWidgetProvider {

    WidgetKind kind();

    WidgetData fetch(String userSub, WidgetSection section);
}
