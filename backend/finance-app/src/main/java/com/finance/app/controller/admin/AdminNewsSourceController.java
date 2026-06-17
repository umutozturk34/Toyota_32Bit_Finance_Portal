package com.finance.app.controller.admin;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.news.service.source.NewsSourceAdminService;
import com.finance.news.service.source.NewsSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only CRUD and enable/disable API for news source configurations. */
@RestController
@RequestMapping("/api/v1/admin/news-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminNewsSourceController {

    private final NewsSourceService newsSourceService;
    private final NewsSourceAdminService newsSourceAdminService;
    private final Translator translator;

    /** Lists all news sources; disabled ones are included unless {@code includeDisabled} is false. */
    @GetMapping
    public ApiResponse<List<NewsSourceResponse>> getAll(
            @RequestParam(defaultValue = "true") boolean includeDisabled
    ) {
        List<NewsSourceResponse> data = newsSourceService.getAllSources(includeDisabled);
        return ApiResponse.success(translator.translate("api.newsSource.listRetrieved"), data);
    }

    /** Fetches a single news source by id. */
    @GetMapping("/{id}")
    public ApiResponse<NewsSourceResponse> getById(@PathVariable Long id) {
        NewsSourceResponse data = newsSourceService.getById(id);
        return ApiResponse.success(translator.translate("api.newsSource.retrieved"), data);
    }

    /** Creates a new news source from the request. */
    @PostMapping
    public ApiResponse<NewsSourceResponse> create(
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.create(request);
        return ApiResponse.success(translator.translate("api.newsSource.created"), data);
    }

    /** Replaces the configuration of the news source with the given id. */
    @PutMapping("/{id}")
    public ApiResponse<NewsSourceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpsertNewsSourceRequest request
    ) {
        NewsSourceResponse data = newsSourceAdminService.update(id, request);
        return ApiResponse.success(translator.translate("api.newsSource.updated"), data);
    }

    /** Enables or disables the news source (a disabled source is skipped during news fetches). */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> setEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled
    ) {
        newsSourceAdminService.setEnabled(id, enabled);
        return ApiResponse.success(translator.translate("api.newsSource.statusUpdated"), null);
    }

    /** Permanently removes the news source. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        newsSourceAdminService.delete(id);
        return ApiResponse.success(translator.translate("api.newsSource.deleted"), null);
    }
}
