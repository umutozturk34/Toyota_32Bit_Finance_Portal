package com.finance.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.service.UserLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/api/v1/user/layout")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserLayoutController {

    private final UserLayoutService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<UserLayoutResponse> getLayout(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.layout.retrieved"), service.getOrEmpty(jwt.getSubject()));
    }

    @PutMapping("/overview")
    public ApiResponse<UserLayoutResponse> updateOverview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode overview) {
        return ApiResponse.success(translator.translate("api.layout.overviewUpdated"), service.saveOverview(jwt.getSubject(), overview));
    }
}
