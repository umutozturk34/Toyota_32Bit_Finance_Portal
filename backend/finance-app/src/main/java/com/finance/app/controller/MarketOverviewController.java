package com.finance.app.controller;

import com.finance.app.dto.response.overview.RenderedWidget;
import com.finance.app.dto.response.overview.WidgetDefinitionResponse;
import com.finance.app.service.overview.MarketOverviewService;
import com.finance.app.service.overview.WidgetDefinitionService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/overview")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService marketOverviewService;
    private final WidgetDefinitionService widgetDefinitionService;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RenderedWidget>> getOverview(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.market.overviewRetrieved"), marketOverviewService.render(jwt.getSubject()));
    }

    @GetMapping("/widget-definitions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<WidgetDefinitionResponse> getWidgetDefinitions() {
        return ApiResponse.success(translator.translate("api.market.widgetDefinitionsRetrieved"), widgetDefinitionService.build());
    }
}
