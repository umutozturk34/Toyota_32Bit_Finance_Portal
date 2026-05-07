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

@Log4j2
@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    private final OverviewLayoutReader layoutReader;
    private final WidgetProviderRegistry registry;

    public List<RenderedWidget> render(String userSub) {
        List<WidgetSection> sections = layoutReader.readVisibleSections(userSub);
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
