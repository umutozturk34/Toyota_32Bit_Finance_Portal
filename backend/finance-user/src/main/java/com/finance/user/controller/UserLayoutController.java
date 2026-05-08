package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.service.UserLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Log4j2
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
            @RequestBody Map<String, Object> overview) {
        return ApiResponse.success("Overview layout updated", service.saveOverview(jwt.getSubject(), overview));
    }
}
