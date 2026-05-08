package com.finance.user.controller;


import com.finance.user.dto.AdminUserResponse;
import com.finance.common.dto.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int first,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int max,
            @RequestParam(required = false) String search) {
        return ApiResponse.success("Users retrieved", service.listUsers(first, max, search));
    }

    @GetMapping("/count")
    public ApiResponse<Long> countUsers(@RequestParam(required = false) String search) {
        return ApiResponse.success("User count retrieved", service.countUsers(search));
    }

    @PutMapping("/{id}/ban")
    public ApiResponse<Void> banUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        service.banUser(id, jwt.getSubject());
        return ApiResponse.success("User banned", null);
    }

    @PutMapping("/{id}/unban")
    public ApiResponse<Void> unbanUser(@PathVariable String id) {
        service.unbanUser(id);
        return ApiResponse.success("User unbanned", null);
    }
}
