package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.request.UpsertNewsSourceRequest;
import com.finance.backend.dto.response.NewsSourceResponse;
import com.finance.backend.service.NewsSourceAdminService;
import com.finance.backend.service.NewsSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<List<NewsSourceResponse>>> getAll(
            @RequestParam(defaultValue = "true") boolean includeDisabled
    ) {
        List<NewsSourceResponse> data = newsSourceService.getAllSources(includeDisabled);
        return ResponseEntity.ok(ApiResponse.success("News sources retrieved", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsSourceResponse>> getById(@PathVariable Long id) {
        NewsSourceResponse data = newsSourceService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("News source retrieved", data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NewsSourceResponse>> create(
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.create(request);
        return ResponseEntity.ok(ApiResponse.success("News source created", data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsSourceResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("News source updated", data));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Void>> setEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled
    ) {
        newsSourceAdminService.setEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success("News source status updated", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        newsSourceAdminService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("News source deleted", null));
    }
}
