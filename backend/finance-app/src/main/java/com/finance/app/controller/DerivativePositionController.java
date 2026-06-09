package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.service.DerivativePositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for a portfolio's derivative (VIOP future/option) positions: list, open, close, update an open or
 * a closed position, reopen, and delete. All operations are scoped to the owning portfolio and JWT subject.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/derivatives")
@RequiredArgsConstructor
@Validated
public class DerivativePositionController {

    private final DerivativePositionService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<List<DerivativePositionResponse>> list(@PathVariable Long portfolioId,
                                                              @RequestParam(defaultValue = "false") boolean openOnly,
                                                              @AuthenticationPrincipal Jwt jwt) {
        List<DerivativePositionResponse> data = openOnly
                ? service.listOpen(portfolioId, jwt.getSubject())
                : service.list(portfolioId, jwt.getSubject());
        return ApiResponse.success(data);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DerivativePositionResponse> open(@PathVariable Long portfolioId,
                                                        @Valid @RequestBody OpenDerivativePositionRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        DerivativePositionResponse created = service.open(portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.derivative.opened"), created);
    }

    /** Closes an open position (records the exit). Use the PUT variant to amend an already-closed position. */
    @PatchMapping("/{positionId}/close")
    public ApiResponse<DerivativePositionResponse> close(@PathVariable Long portfolioId,
                                                         @PathVariable Long positionId,
                                                         @Valid @RequestBody CloseDerivativePositionRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        DerivativePositionResponse updated = service.close(positionId, portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.derivative.closed"), updated);
    }

    /** Amends the exit details of an already-closed position. */
    @PutMapping("/{positionId}/close")
    public ApiResponse<DerivativePositionResponse> updateClose(@PathVariable Long portfolioId,
                                                                @PathVariable Long positionId,
                                                                @Valid @RequestBody CloseDerivativePositionRequest request,
                                                                @AuthenticationPrincipal Jwt jwt) {
        DerivativePositionResponse updated = service.updateClose(positionId, portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.derivative.closeUpdated"), updated);
    }

    @PutMapping("/{positionId}")
    public ApiResponse<DerivativePositionResponse> update(@PathVariable Long portfolioId,
                                                           @PathVariable Long positionId,
                                                           @Valid @RequestBody UpdateDerivativePositionRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        DerivativePositionResponse updated = service.updateOpen(positionId, portfolioId, jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.derivative.updated"), updated);
    }

    @PatchMapping("/{positionId}/reopen")
    public ApiResponse<DerivativePositionResponse> reopen(@PathVariable Long portfolioId,
                                                           @PathVariable Long positionId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        DerivativePositionResponse updated = service.reopen(positionId, portfolioId, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.derivative.reopened"), updated);
    }

    @DeleteMapping("/{positionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long portfolioId,
                       @PathVariable Long positionId,
                       @AuthenticationPrincipal Jwt jwt) {
        service.delete(positionId, portfolioId, jwt.getSubject());
    }
}
