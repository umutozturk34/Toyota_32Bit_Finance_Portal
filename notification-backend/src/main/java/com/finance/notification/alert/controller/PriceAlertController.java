package com.finance.notification.alert.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.dto.PriceAlertUpdateRequest;
import com.finance.notification.alert.service.PriceAlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for managing a user's price alerts. All endpoints are scoped to the authenticated
 * caller's JWT subject; alerts created here are later evaluated against the market snapshot cache.
 */
@RestController
@RequestMapping("/api/v1/price-alerts")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class PriceAlertController {

    private final PriceAlertService service;
    private final Translator translator;

    @PostMapping
    public ApiResponse<PriceAlertResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PriceAlertCreateRequest request) {
        return ApiResponse.success(translator.translate("api.priceAlert.created"),
                service.create(jwt.getSubject(), request));
    }

    @GetMapping
    public ApiResponse<PagedResponse<PriceAlertResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<PriceAlertResponse> result = service.list(jwt.getSubject(), page, size);
        return ApiResponse.success(translator.translate("api.priceAlert.listRetrieved"),
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.delete(id, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.priceAlert.deleted"), null);
    }

    /**
     * Re-arms a previously triggered alert so it can fire again; rejected if an identical active
     * alert already exists for the same asset, direction and threshold.
     */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/reactivate")
    public ApiResponse<PriceAlertResponse> reactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ApiResponse.success(translator.translate("api.priceAlert.reactivated"),
                service.reactivate(id, jwt.getSubject()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PriceAlertResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody PriceAlertUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.priceAlert.updated"),
                service.update(id, jwt.getSubject(), request));
    }
}
