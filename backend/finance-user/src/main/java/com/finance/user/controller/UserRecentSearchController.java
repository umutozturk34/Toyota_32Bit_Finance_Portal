package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.RecentSearchItem;
import com.finance.user.dto.RecordRecentSearchRequest;
import com.finance.user.service.UserRecentSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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

/**
 * REST API for the current user's recent search selections: lists them (newest first), records a new
 * selection, and clears them. All endpoints are authenticated and scoped to the JWT subject.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/user/recent-searches")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserRecentSearchController {

    private final UserRecentSearchService service;
    private final Translator translator;

    /** The user's recent search selections, newest first. */
    @GetMapping
    public ApiResponse<List<RecentSearchItem>> getRecentSearches(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.recentSearches.retrieved"), service.getItems(jwt.getSubject()));
    }

    /** Records a search selection (deduplicating and capping history), returning the updated list. */
    @PostMapping
    public ApiResponse<List<RecentSearchItem>> recordRecentSearch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecordRecentSearchRequest request) {
        return ApiResponse.success(translator.translate("api.recentSearches.recorded"), service.record(jwt.getSubject(), request));
    }

    /** Clears the user's entire recent-search history. */
    @DeleteMapping
    public ApiResponse<Void> clearRecentSearches(@AuthenticationPrincipal Jwt jwt) {
        service.clear(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.recentSearches.cleared"), null);
    }

    /** Removes a single recent search identified by {@code type}/{@code code}, returning the updated list. */
    @DeleteMapping("/{type}/{code}")
    public ApiResponse<List<RecentSearchItem>> removeRecentSearch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String type,
            @PathVariable String code) {
        return ApiResponse.success(translator.translate("api.recentSearches.removed"),
                service.remove(jwt.getSubject(), code, type));
    }
}
