package com.finance.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.UserLayoutResponse;
import com.finance.backend.service.UserLayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/layout")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserLayoutController {

    private final UserLayoutService service;

    @GetMapping
    public ApiResponse<UserLayoutResponse> getLayout(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Layout retrieved", service.getOrEmpty(jwt.getSubject()));
    }

    @PutMapping("/overview")
    public ApiResponse<UserLayoutResponse> updateOverview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode overview) {
        return ApiResponse.success("Overview layout updated", service.saveOverview(jwt.getSubject(), overview));
    }
}
