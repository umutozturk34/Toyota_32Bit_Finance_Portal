package com.finance.backend.controller;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.PositionRequest;
import com.finance.backend.dto.response.*;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.service.PortfolioFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final PortfolioResponseMapper mapper;

    @GetMapping
    public ApiResponse<List<PortfolioResponse>> listPortfolios(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Portfolios retrieved",
                portfolioFacade.listPortfolios(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortfolioResponse> createPortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PortfolioCreateRequest request) {
        return ApiResponse.success("Portfolio created",
                portfolioFacade.createPortfolio(jwt.getSubject(), request));
    }

    @PostMapping("/{portfolioId}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PositionResponse> addPosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @Valid @RequestBody PositionRequest request) {
        PortfolioPosition saved = portfolioFacade.addPosition(jwt.getSubject(), portfolioId, request);
        return ApiResponse.success("Position created", toResponseShell(saved));
    }

    @PutMapping("/{portfolioId}/positions/{positionId}")
    public ApiResponse<PositionResponse> updatePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId,
            @Valid @RequestBody PositionRequest request) {
        PortfolioPosition saved = portfolioFacade.updatePosition(jwt.getSubject(), portfolioId, positionId, request);
        return ApiResponse.success("Position updated", toResponseShell(saved));
    }

    @DeleteMapping("/{portfolioId}/positions/{positionId}")
    public ApiResponse<Void> deletePosition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @PathVariable Long positionId) {
        portfolioFacade.deletePosition(jwt.getSubject(), portfolioId, positionId);
        return ApiResponse.success("Position deleted", null);
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
        return ApiResponse.success("Positions retrieved",
                portfolioFacade.getPositionsPaged(jwt.getSubject(), portfolioId,
                        search, assetType, sort, direction, page, resolvedSize));
    }

    @GetMapping("/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String assetType) {
        return ApiResponse.success("Summary retrieved",
                portfolioFacade.getSummary(jwt.getSubject(), portfolioId, assetType));
    }

    @GetMapping("/{portfolioId}/allocation")
    public ApiResponse<List<AllocationItem>> getAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "assetType") String mode,
            @RequestParam(required = false) String assetType) {
        return ApiResponse.success("Allocation retrieved",
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
        return ApiResponse.success("Portfolio view retrieved",
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
        return ApiResponse.success("Chart data retrieved",
                portfolioFacade.getChart(jwt.getSubject(), portfolioId, type, range, assetType, assetCode));
    }

    private PositionResponse toResponseShell(PortfolioPosition position) {
        return mapper.toPositionResponse(position, BigDecimal.ZERO, position.entryValue(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    private int resolvePageSize(Integer size, int defaultSize) {
        int requestedSize = size == null ? defaultSize : size;
        return Math.max(1, Math.min(requestedSize, appProperties.getPagination().getPortfolio().getMaxSize()));
    }
}
