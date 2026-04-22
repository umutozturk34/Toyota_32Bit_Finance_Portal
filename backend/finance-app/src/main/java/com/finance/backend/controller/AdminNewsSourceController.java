package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.request.UpsertNewsSourceRequest;
import com.finance.backend.dto.response.NewsSourceResponse;
import com.finance.backend.service.NewsSourceAdminService;
import com.finance.backend.service.NewsSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/news-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNewsSourceController {

    private final NewsSourceService newsSourceService;
    private final NewsSourceAdminService newsSourceAdminService;

    @GetMapping
    public ApiResponse<List<NewsSourceResponse>> getAll(
            @RequestParam(defaultValue = "true") boolean includeDisabled
    ) {
        List<NewsSourceResponse> data = newsSourceService.getAllSources(includeDisabled);
        return ApiResponse.success("News sources retrieved", data);
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsSourceResponse> getById(@PathVariable Long id) {
        NewsSourceResponse data = newsSourceService.getById(id);
        return ApiResponse.success("News source retrieved", data);
    }

    @PostMapping
    public ApiResponse<NewsSourceResponse> create(
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.create(request);
        return ApiResponse.success("News source created", data);
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsSourceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.update(id, request);
        return ApiResponse.success("News source updated", data);
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> setEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled
    ) {
        newsSourceAdminService.setEnabled(id, enabled);
        return ApiResponse.success("News source status updated", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        newsSourceAdminService.delete(id);
        return ApiResponse.success("News source deleted", null);
    }
}
