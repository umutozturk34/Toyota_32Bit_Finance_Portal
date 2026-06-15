package com.finance.portfolio.controller;

import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.BulkDeleteRequest;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST API for portfolios and their spot positions: list/create/rename/delete portfolios,
 * add/update/sell/reopen/delete positions, and read summary/positions/allocation/view/chart plus the
 * SSE backfill-status stream. All endpoints are authenticated and scoped to the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class PortfolioController {

    private static final String DEFAULT_VIEW_INCLUDES = "summary,positions,allocation";
    private static final String DEFAULT_CHART_RANGE = "1M";
    private static final String DEFAULT_ALLOCATION_MODE = "assetType";
    private static final int MIN_PAGE_SIZE = 1;
    private static final String INCLUDES_SEPARATOR = ",";

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

    @PostMapping("/{portfolioId}/positions/bulk-delete")
    public ApiResponse<Void> bulkDeletePositions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @Valid @RequestBody BulkDeleteRequest request) {
        portfolioFacade.bulkDeletePositions(jwt.getSubject(), portfolioId, request.ids());
        return ApiResponse.success(translator.translate("api.portfolio.positionDeleted"), null);
    }

    @PostMapping("/{portfolioId}/positions/{positionId}/sell")
    public ApiResponse<PositionResponse> sellPosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId,
            @Valid @RequestBody PositionSellRequest request) {
        return ApiResponse.success(translator.translate("api.portfolio.positionSold"),
                portfolioFacade.sellPosition(jwt.getSubject(), portfolioId, positionId, request));
    }

    @PostMapping("/{portfolioId}/positions/{positionId}/reopen")
    public ApiResponse<PositionResponse> reopenPosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId) {
        return ApiResponse.success(translator.translate("api.portfolio.positionReopened"),
                portfolioFacade.reopenPosition(jwt.getSubject(), portfolioId, positionId));
    }

    @PutMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponse> renamePortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @Valid @RequestBody PortfolioCreateRequest request) {
        return ApiResponse.success(translator.translate("api.portfolio.renamed"),
                portfolioFacade.renamePortfolio(jwt.getSubject(), portfolioId, request));
    }

    @DeleteMapping("/{portfolioId}")
    public ApiResponse<Void> deletePortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId) {
        portfolioFacade.deletePortfolio(jwt.getSubject(), portfolioId);
        return ApiResponse.success(translator.translate("api.portfolio.deleted"), null);
    }

    @GetMapping("/{portfolioId}/assets/{assetType}/{assetCode}/summary")
    public ApiResponse<AssetAggregateResponse> getAssetAggregate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable String assetType,
            @PathVariable String assetCode,
            @RequestParam(required = false) String direction) {
        return ApiResponse.success(translator.translate("api.portfolio.assetAggregateRetrieved"),
                portfolioFacade.getAssetAggregate(jwt.getSubject(), portfolioId, assetType, assetCode, direction));
    }

    @GetMapping("/{portfolioId}/positions")
    public ApiResponse<PagedResponse<PositionResponse>> getPositions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Boolean closed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = resolvePageSize(size, appProperties.getPagination().getPortfolio().getPositionsDefaultSize());
        return ApiResponse.success(translator.translate("api.portfolio.positionsRetrieved"),
                portfolioFacade.getPositionsPaged(jwt.getSubject(), portfolioId,
                        search, assetType, sort, direction, closed, page, resolvedSize));
    }

    @GetMapping("/{portfolioId}/positions/by-asset")
    public ApiResponse<List<PositionResponse>> getPositionsByAsset(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam String assetType,
            @RequestParam String assetCode) {
        return ApiResponse.success(translator.translate("api.portfolio.positionsRetrieved"),
                portfolioFacade.getPositionsByAsset(jwt.getSubject(), portfolioId, assetType, assetCode));
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
            @RequestParam(defaultValue = DEFAULT_ALLOCATION_MODE) String mode,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(translator.translate("api.portfolio.allocationRetrieved"),
                portfolioFacade.getAllocation(jwt.getSubject(), portfolioId, mode, assetType, limit));
    }

    @GetMapping("/{portfolioId}/view")
    public ApiResponse<PortfolioViewResponse> getPortfolioView(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = DEFAULT_VIEW_INCLUDES) String include) {
        Set<String> includes = Arrays.stream(include.split(INCLUDES_SEPARATOR))
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
            @RequestParam(defaultValue = DEFAULT_CHART_RANGE) String range,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) String direction) {
        return ApiResponse.success(translator.translate("api.portfolio.chartRetrieved"),
                portfolioFacade.getChart(jwt.getSubject(), portfolioId, type, range, assetType, assetCode, direction));
    }

    private int resolvePageSize(Integer size, int defaultSize) {
        int requestedSize = size == null ? defaultSize : size;
        int maxSize = appProperties.getPagination().getPortfolio().getMaxSize();
        return Math.max(MIN_PAGE_SIZE, Math.min(requestedSize, maxSize));
    }
}
