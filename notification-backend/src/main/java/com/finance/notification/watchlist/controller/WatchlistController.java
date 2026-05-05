package com.finance.notification.watchlist.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.notification.watchlist.dto.WatchlistCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.dto.WatchlistRenameRequest;
import com.finance.notification.watchlist.dto.WatchlistReorderRequest;
import com.finance.notification.watchlist.dto.WatchlistResponse;
import com.finance.notification.watchlist.model.WatchlistSortBy;
import com.finance.notification.watchlist.service.WatchlistManagementService;
import com.finance.notification.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlists")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistManagementService managementService;
    private final WatchlistService watchlistService;

    @GetMapping
    public ApiResponse<List<WatchlistResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Watchlists retrieved",
                managementService.list(jwt.getSubject()));
    }

    @PostMapping
    public ApiResponse<WatchlistResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistCreateRequest request) {
        return ApiResponse.success("Watchlist created",
                managementService.create(jwt.getSubject(), request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WatchlistResponse> rename(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody WatchlistRenameRequest request) {
        return ApiResponse.success("Watchlist renamed",
                managementService.rename(id, jwt.getSubject(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        managementService.delete(id, jwt.getSubject());
        return ApiResponse.success("Watchlist deleted", null);
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<WatchlistItemResponse>> listItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam(defaultValue = "CUSTOM") WatchlistSortBy sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return ApiResponse.success("Watchlist items retrieved",
                watchlistService.listItems(id, jwt.getSubject(), sort, direction));
    }

    @PostMapping("/{id}/items")
    public ApiResponse<WatchlistItemResponse> addItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody WatchlistItemCreateRequest request) {
        return ApiResponse.success("Watchlist item added",
                watchlistService.addToList(id, jwt.getSubject(), request));
    }

    @PutMapping("/{id}/items/reorder")
    public ApiResponse<List<WatchlistItemResponse>> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody WatchlistReorderRequest request) {
        return ApiResponse.success("Watchlist items reordered",
                watchlistService.reorder(id, jwt.getSubject(), request.itemIds()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId) {
        watchlistService.removeItem(itemId, jwt.getSubject());
        return ApiResponse.success("Watchlist item removed", null);
    }

    @PostMapping("/favorites/items")
    public ApiResponse<WatchlistItemResponse> addToDefault(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistItemCreateRequest request) {
        return ApiResponse.success("Added to default watchlist",
                watchlistService.addToDefault(jwt.getSubject(), request));
    }
}
