package com.finance.notification.watchlist.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService service;

    @PostMapping
    public ApiResponse<WatchlistItemResponse> add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistItemCreateRequest request) {
        return ApiResponse.success("Watchlist item added",
                service.add(jwt.getSubject(), request));
    }

    @GetMapping
    public ApiResponse<List<WatchlistItemResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Watchlist retrieved",
                service.list(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.remove(id, jwt.getSubject());
        return ApiResponse.success("Watchlist item removed", null);
    }
}
