package com.finance.user.controller;


import com.finance.user.dto.AdminUserResponse;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.service.AdminUserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only REST API for user administration: paged listing/count and ban/unban (account
 * enable/disable) of Keycloak users. All endpoints require the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService service;
    private final Translator translator;

    /** Paged listing of Keycloak users, optionally filtered by {@code search}; {@code first} is a row offset, {@code max} is capped at 200. */
    @GetMapping
    public ApiResponse<List<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int first,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int max,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(translator.translate("api.admin.usersRetrieved"), service.listUsers(first, max, search));
    }

    /** Total number of users matching {@code search} (for paging the listing). */
    @GetMapping("/count")
    public ApiResponse<Long> countUsers(@RequestParam(required = false) String search) {
        return ApiResponse.success(translator.translate("api.admin.userCountRetrieved"), service.countUsers(search));
    }

    /** Bans (disables) the user {@code id}; the acting admin's subject is passed through for audit/self-ban guarding. */
    @PutMapping("/{id}/ban")
    public ApiResponse<Void> banUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        service.banUser(id, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.admin.userBanned"), null);
    }

    /** Unbans (re-enables) the user {@code id}. */
    @PutMapping("/{id}/unban")
    public ApiResponse<Void> unbanUser(@PathVariable String id) {
        service.unbanUser(id);
        return ApiResponse.success(translator.translate("api.admin.userUnbanned"), null);
    }
}
