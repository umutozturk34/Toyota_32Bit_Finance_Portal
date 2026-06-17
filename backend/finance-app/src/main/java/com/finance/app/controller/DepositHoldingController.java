package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.DepositHoldingRequest;
import com.finance.portfolio.dto.response.DepositHoldingResponse;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingService;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for a portfolio's hypothetical DEPOSIT (mevduat) holdings: list, add, update, close, reopen, and
 * delete. All operations are scoped to the owning portfolio and the JWT subject; ownership and not-found
 * checks live in {@link DepositHoldingService}, so this layer only delegates and wraps the result envelope.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/deposits")
@RequiredArgsConstructor
@Validated
public class DepositHoldingController {

    private final DepositHoldingService service;
    private final Translator translator;

    /** All deposit holdings (active and closed) of the portfolio. */
    @GetMapping
    public ApiResponse<List<DepositHoldingResponse>> list(@PathVariable Long portfolioId,
                                                          @AuthenticationPrincipal Jwt jwt) {
        List<DepositHoldingResponse> data = service.list(portfolioId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.deposit.listed"), data);
    }

    /** Adds a new deposit holding to the portfolio. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DepositHoldingResponse> add(@PathVariable Long portfolioId,
                                                   @Valid @RequestBody DepositHoldingRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        DepositHoldingResponse created = service.add(portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.portfolio.deposit.created"), created);
    }

    /** Edits an existing deposit holding (e.g. principal, rate, or term). */
    @PutMapping("/{holdingId}")
    public ApiResponse<DepositHoldingResponse> update(@PathVariable Long portfolioId,
                                                      @PathVariable Long holdingId,
                                                      @Valid @RequestBody DepositHoldingRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        DepositHoldingResponse updated = service.update(portfolioId, holdingId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.portfolio.deposit.updated"), updated);
    }

    /**
     * Closes an active deposit, freezing its accrued value. The optional {@code closeDate} defaults to today
     * when omitted; the service rejects closing an already-closed deposit.
     */
    @PostMapping("/{holdingId}/close")
    public ApiResponse<DepositHoldingResponse> close(@PathVariable Long portfolioId,
                                                     @PathVariable Long holdingId,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeDate,
                                                     @AuthenticationPrincipal Jwt jwt) {
        DepositHoldingResponse closed = service.close(portfolioId, holdingId, jwt.getSubject(), closeDate);
        return ApiResponse.success(translator.translate("api.portfolio.deposit.closed"), closed);
    }

    /** Reverses a {@code close}, returning a closed deposit to the active state so it resumes accruing. */
    @PostMapping("/{holdingId}/reopen")
    public ApiResponse<DepositHoldingResponse> reopen(@PathVariable Long portfolioId,
                                                      @PathVariable Long holdingId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        DepositHoldingResponse reopened = service.reopen(portfolioId, holdingId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.deposit.reopened"), reopened);
    }

    /** Permanently removes a deposit holding from the portfolio. */
    @DeleteMapping("/{holdingId}")
    public ApiResponse<Void> delete(@PathVariable Long portfolioId,
                                    @PathVariable Long holdingId,
                                    @AuthenticationPrincipal Jwt jwt) {
        service.delete(portfolioId, holdingId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.portfolio.deposit.deleted"), null);
    }
}
