package com.finance.app.controller.admin;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.news.service.source.NewsSourceAdminService;
import com.finance.news.service.source.NewsSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminNewsSourceControllerTest {

    @Mock private NewsSourceService newsSourceService;
    @Mock private NewsSourceAdminService newsSourceAdminService;
    @Mock private Translator translator;

    private AdminNewsSourceController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminNewsSourceController(newsSourceService, newsSourceAdminService, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NewsSourceResponse sample() {
        return new NewsSourceResponse(1L, "Reuters", "https://example.com/rss",
                "RSS", "WORLD", true, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void getAll_delegatesIncludeDisabledFlag_toService() {
        when(newsSourceService.getAllSources(true)).thenReturn(List.of(sample()));

        ApiResponse<List<NewsSourceResponse>> response = controller.getAll(true);

        assertThat(response.getData()).hasSize(1);
        verify(newsSourceService).getAllSources(true);
    }

    @Test
    void getById_delegatesToService_andReturnsResponse() {
        when(newsSourceService.getById(1L)).thenReturn(sample());

        ApiResponse<NewsSourceResponse> response = controller.getById(1L);

        assertThat(response.getData().id()).isEqualTo(1L);
    }

    @Test
    void create_delegatesToAdminService_andReturnsCreated() {
        UpsertNewsSourceRequest request = new UpsertNewsSourceRequest();
        request.setName("New");
        request.setUrl("https://x");
        when(newsSourceAdminService.create(request)).thenReturn(sample());

        ApiResponse<NewsSourceResponse> response = controller.create(request);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void update_delegatesToAdminService_andReturnsUpdated() {
        UpsertNewsSourceRequest request = new UpsertNewsSourceRequest();
        request.setName("Renamed");
        request.setUrl("https://x");
        when(newsSourceAdminService.update(1L, request)).thenReturn(sample());

        ApiResponse<NewsSourceResponse> response = controller.update(1L, request);

        assertThat(response.getData().name()).isEqualTo("Reuters");
    }

    @Test
    void setEnabled_invokesAdminService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.setEnabled(1L, false);

        assertThat(response.isSuccess()).isTrue();
        verify(newsSourceAdminService).setEnabled(1L, false);
    }

    @Test
    void delete_invokesAdminService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.delete(5L);

        assertThat(response.isSuccess()).isTrue();
        verify(newsSourceAdminService).delete(5L);
    }
}
