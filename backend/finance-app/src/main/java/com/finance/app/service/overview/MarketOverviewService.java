package com.finance.app.service.overview;

import com.finance.app.dto.response.overview.RenderedWidget;
import com.finance.app.dto.response.overview.WidgetData;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders a user's overview dashboard: reads their visible widget sections and asks the matching provider
 * to fetch each widget's data. A missing provider or a downstream IO failure yields an empty widget (so the
 * rest of the dashboard still renders), while business-rule violations are propagated.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    private final OverviewLayoutReader layoutReader;
    private final WidgetProviderRegistry registry;

    public List<RenderedWidget> render(String userSub) {
        return render(userSub, null);
    }

    public List<RenderedWidget> render(String userSub, String pageId) {
        List<WidgetSection> sections = layoutReader.readVisibleSections(userSub, pageId);
        List<RenderedWidget> rendered = new ArrayList<>(sections.size());
        for (WidgetSection section : sections) {
            rendered.add(renderOne(userSub, section));
        }
        return rendered;
    }

    private RenderedWidget renderOne(String userSub, WidgetSection section) {
        Optional<OverviewWidgetProvider> provider = registry.providerFor(section.kind());
        if (provider.isEmpty()) {
            log.warn("MarketOverview no provider for kind={}, sectionId={}", section.kind(), section.sectionId());
            return RenderedWidget.empty(section.sectionId(), section.kind(), section.order());
        }
        try {
            WidgetData data = provider.get().fetch(userSub, section);
            return RenderedWidget.of(section.sectionId(), section.order(), data);
        } catch (DataAccessException | RestClientException ex) {
            log.warn("MarketOverview provider {} downstream IO failure for sectionId={}: {}",
                    section.kind(), section.sectionId(), ex.getMessage(), ex);
            return RenderedWidget.empty(section.sectionId(), section.kind(), section.order());
        } catch (BusinessException ex) {
            throw ex;
        }
    }
}
