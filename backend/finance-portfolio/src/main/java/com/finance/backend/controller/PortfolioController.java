package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.*;
import com.finance.backend.service.PortfolioFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioFacade portfolioFacade;

    @PostMapping("/onboarding/initialize")
    public ResponseEntity<ApiResponse<Void>> initialize(@AuthenticationPrincipal Jwt jwt) {
        portfolioFacade.initialize(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Onboarding completed", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> listPortfolios(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success("Portfolios retrieved",
                portfolioFacade.listPortfolios(jwt.getSubject())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> createPortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PortfolioCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Portfolio created",
                        portfolioFacade.createPortfolio(jwt.getSubject(), request)));
    }

    @PostMapping("/{portfolioId}/transactions")
    public ResponseEntity<ApiResponse<TransactionResponse>> executeTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction executed",
                        portfolioFacade.executeTransaction(jwt.getSubject(), portfolioId, request)));
    }

    @GetMapping("/{portfolioId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> listTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved",
                portfolioFacade.listTransactions(jwt.getSubject(), portfolioId)));
    }

    @GetMapping("/{portfolioId}/positions")
    public ResponseEntity<ApiResponse<List<PositionResponse>>> getPositions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(ApiResponse.success("Positions retrieved",
                portfolioFacade.getPositions(jwt.getSubject(), portfolioId)));
    }

    @GetMapping("/{portfolioId}/summary")
    public ResponseEntity<ApiResponse<PortfolioSummaryResponse>> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(ApiResponse.success("Summary retrieved",
                portfolioFacade.getSummary(jwt.getSubject(), portfolioId)));
    }

    @GetMapping("/{portfolioId}/allocation")
    public ResponseEntity<ApiResponse<List<AllocationItem>>> getAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "assetType") String mode) {
        return ResponseEntity.ok(ApiResponse.success("Allocation retrieved",
                portfolioFacade.getAllocation(jwt.getSubject(), portfolioId, mode)));
    }

    @GetMapping("/{portfolioId}/performance")
    public ResponseEntity<ApiResponse<List<PerformancePoint>>> getPerformance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(required = false) String assetType) {
        return ResponseEntity.ok(ApiResponse.success("Performance retrieved",
                portfolioFacade.getPerformance(jwt.getSubject(), portfolioId, range, assetType)));
    }

    @GetMapping("/{portfolioId}/asset-series")
    public ResponseEntity<ApiResponse<List<AssetSeriesPoint>>> getAssetSeries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long portfolioId,
            @RequestParam String assetType,
            @RequestParam String assetCode,
            @RequestParam(defaultValue = "1M") String range) {
        return ResponseEntity.ok(ApiResponse.success("Asset series retrieved",
                portfolioFacade.getAssetSeries(jwt.getSubject(), portfolioId, assetType, assetCode, range)));
    }
}
