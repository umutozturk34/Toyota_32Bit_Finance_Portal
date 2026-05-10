package com.finance.portfolio.controller;
import com.finance.portfolio.dto.response.PositionResponse;

import com.finance.portfolio.dto.response.PortfolioViewResponse;

import com.finance.portfolio.dto.response.PortfolioSummaryResponse;

import com.finance.portfolio.dto.response.PortfolioResponse;

import com.finance.portfolio.model.Portfolio;

import com.finance.common.dto.response.PagedResponse;

import com.finance.portfolio.dto.response.LotLimitsResponse;

import com.finance.portfolio.dto.response.AllocationItem;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.LotLimitsResponse;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PortfolioViewResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.service.PortfolioBackfillTracker;
import com.finance.portfolio.service.PortfolioFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/portfolios")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PortfolioController {

    private final AppProperties appProperties;
    private final PortfolioFacade portfolioFacade;
    private final PortfolioBackfillTracker backfillTracker;
    private final Translator translator;

    @GetMapping
    public ApiResponse<List<PortfolioResponse>> listPortfolios(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.portfolio.listRetrieved"),
                portfolioFacade.listPortfolios(jwt.getSubject()));
    }

    @GetMapping("/limits")
    public ApiResponse<LotLimitsResponse> getLotLimits() {
        return ApiResponse.success(translator.translate("api.portfolio.lotLimitsRetrieved"), portfolioFacade.getLotLimits());
    }

    @GetMapping(path = "/{portfolioId}/backfill-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBackfillStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId) {
        portfolioFacade.requireOwnership(jwt.getSubject(), portfolioId);
        return backfillTracker.subscribe(portfolioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortfolioResponse> createPortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PortfolioCreateRequest request) {
        return ApiResponse.success(translator.translate("api.portfolio.created"),
                portfolioFacade.createPortfolio(jwt.getSubject(), request));
    }

    @PostMapping("/{portfolioId}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PositionResponse> addPosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @Valid @RequestBody PositionRequest request) {
        return ApiResponse.success(translator.translate("api.portfolio.positionCreated"),
                portfolioFacade.addPosition(jwt.getSubject(), portfolioId, request));
    }

    @PutMapping("/{portfolioId}/positions/{positionId}")
    public ApiResponse<PositionResponse> updatePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId,
            @Valid @RequestBody PositionRequest request) {
        return ApiResponse.success(translator.translate("api.portfolio.positionUpdated"),
                portfolioFacade.updatePosition(jwt.getSubject(), portfolioId, positionId, request));
    }

    @DeleteMapping("/{portfolioId}/positions/{positionId}")
    public ApiResponse<Void> deletePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId) {
        portfolioFacade.deletePosition(jwt.getSubject(), portfolioId, positionId);
        return ApiResponse.success(translator.translate("api.portfolio.positionDeleted"), null);
    }

    @GetMapping("/{portfolioId}/positions")
    public ApiResponse<PagedResponse<PositionResponse>> getPositions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = resolvePageSize(size, appProperties.getPagination().getPortfolio().getPositionsDefaultSize());
        return ApiResponse.success(translator.translate("api.portfolio.positionsRetrieved"),
                portfolioFacade.getPositionsPaged(jwt.getSubject(), portfolioId,
                        search, assetType, sort, direction, page, resolvedSize));
    }

    @GetMapping("/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String assetType) {
        return ApiResponse.success(translator.translate("api.portfolio.summaryRetrieved"),
                portfolioFacade.getSummary(jwt.getSubject(), portfolioId, assetType));
    }

    @GetMapping("/{portfolioId}/allocation")
    public ApiResponse<List<AllocationItem>> getAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "assetType") String mode,
            @RequestParam(required = false) String assetType) {
        return ApiResponse.success(translator.translate("api.portfolio.allocationRetrieved"),
                portfolioFacade.getAllocation(jwt.getSubject(), portfolioId, mode, assetType));
    }

    @GetMapping("/{portfolioId}/view")
    public ApiResponse<PortfolioViewResponse> getPortfolioView(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "summary,positions,allocation") String include) {
        Set<String> includes = Arrays.stream(include.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        return ApiResponse.success(translator.translate("api.portfolio.viewRetrieved"),
                portfolioFacade.getPortfolioView(jwt.getSubject(), portfolioId, includes));
    }

    @GetMapping("/{portfolioId}/chart")
    public ApiResponse<?> getChart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam String type,
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String assetCode) {
        return ApiResponse.success(translator.translate("api.portfolio.chartRetrieved"),
                portfolioFacade.getChart(jwt.getSubject(), portfolioId, type, range, assetType, assetCode));
    }

    private int resolvePageSize(Integer size, int defaultSize) {
        int requestedSize = size == null ? defaultSize : size;
        return Math.max(1, Math.min(requestedSize, appProperties.getPagination().getPortfolio().getMaxSize()));
    }
}
