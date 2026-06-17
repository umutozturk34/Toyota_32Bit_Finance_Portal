package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import com.finance.portfolio.dto.response.BondCouponScheduleEntry;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.fixedincome.bond.BondHoldingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST API for a portfolio's fixed-income bond (Türkiye Hazine tahvil/bono) holdings: list, add, update,
 * sell (record an exit), reopen, and delete. Bonds are ALWAYS TRY, so no currency parameter is exposed.
 * All operations are scoped to the owning portfolio and the JWT subject; the service throws
 * {@code ResourceNotFoundException} (mapped to 404) when the portfolio or holding is not owned, and
 * {@code BusinessException}/validation errors (mapped to 400) for invalid state or input.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/bonds")
@RequiredArgsConstructor
@Validated
public class BondHoldingController {

    private final BondHoldingService service;
    private final Translator translator;

    /** All bond holdings (open and closed) of the portfolio. */
    @GetMapping
    public ApiResponse<List<BondHoldingResponse>> list(@PathVariable Long portfolioId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        List<BondHoldingResponse> data = service.list(portfolioId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.bond.listed"), data);
    }

    /** The holding's coupon schedule — each coupon priced at its own historical per-period rate (single source). */
    @GetMapping("/{holdingId}/coupon-schedule")
    public ApiResponse<List<BondCouponScheduleEntry>> couponSchedule(@PathVariable Long portfolioId,
                                                                     @PathVariable Long holdingId,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        List<BondCouponScheduleEntry> data = service.couponSchedule(portfolioId, holdingId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.bond.couponSchedule"), data);
    }

    /** Adds a new bond holding to the portfolio. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BondHoldingResponse> add(@PathVariable Long portfolioId,
                                                @Valid @RequestBody BondHoldingRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        BondHoldingResponse created = service.add(portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.portfolio.bond.created"), created);
    }

    /** Edits an existing bond holding (e.g. quantity or entry details). */
    @PutMapping("/{holdingId}")
    public ApiResponse<BondHoldingResponse> update(@PathVariable Long portfolioId,
                                                   @PathVariable Long holdingId,
                                                   @Valid @RequestBody BondHoldingRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        BondHoldingResponse updated = service.update(portfolioId, holdingId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.portfolio.bond.updated"), updated);
    }

    /** Records an exit at {@code exitPrice} (TRY per 100 nominal) on {@code exitDate}, closing the holding. */
    @PostMapping("/{holdingId}/sell")
    public ApiResponse<BondHoldingResponse> sell(@PathVariable Long portfolioId,
                                                 @PathVariable Long holdingId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exitDate,
                                                 @RequestParam @NotNull
                                                 @DecimalMin("0.0001") @DecimalMax("100000") BigDecimal exitPrice,
                                                 @AuthenticationPrincipal Jwt jwt) {
        BondHoldingResponse sold = service.sell(portfolioId, holdingId, jwt.getSubject(), exitDate, exitPrice);
        return ApiResponse.success(translator.translate("api.portfolio.bond.sold"), sold);
    }

    /** Reverses a {@code sell}, returning a closed holding to the open state. */
    @PostMapping("/{holdingId}/reopen")
    public ApiResponse<BondHoldingResponse> reopen(@PathVariable Long portfolioId,
                                                   @PathVariable Long holdingId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        BondHoldingResponse reopened = service.reopen(portfolioId, holdingId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.bond.reopened"), reopened);
    }

    /** Permanently removes a bond holding from the portfolio. */
    @DeleteMapping("/{holdingId}")
    public ApiResponse<Void> delete(@PathVariable Long portfolioId,
                                    @PathVariable Long holdingId,
                                    @AuthenticationPrincipal Jwt jwt) {
        service.delete(portfolioId, holdingId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.bond.deleted"), null);
    }
}
