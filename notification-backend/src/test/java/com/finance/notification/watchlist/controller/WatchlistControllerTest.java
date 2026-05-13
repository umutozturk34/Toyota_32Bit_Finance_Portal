package com.finance.notification.watchlist.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.notification.watchlist.dto.WatchlistCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistItemResponse;
import com.finance.notification.watchlist.dto.WatchlistItemUpdateRequest;
import com.finance.notification.watchlist.dto.WatchlistRenameRequest;
import com.finance.notification.watchlist.dto.WatchlistReorderRequest;
import com.finance.notification.watchlist.dto.WatchlistResponse;
import com.finance.notification.watchlist.model.WatchlistSortBy;
import com.finance.notification.watchlist.service.WatchlistManagementService;
import com.finance.notification.watchlist.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistControllerTest {

    private static final String SUB = "user-1";

    @Mock private WatchlistManagementService managementService;
    @Mock private WatchlistService watchlistService;
    @Mock private Translator translator;

    private WatchlistController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new WatchlistController(managementService, watchlistService, translator);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .claim("sub", SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WatchlistResponse sampleList() {
        return new WatchlistResponse(1L, "Favs", true, 3L,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private WatchlistItemResponse sampleItem() {
        return new WatchlistItemResponse(10L, MarketType.STOCK, "AAPL", "Apple", null,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void list_returnsSuccess_withDataFromManagementService() {
        when(managementService.list(SUB)).thenReturn(List.of(sampleList()));

        ApiResponse<List<WatchlistResponse>> response = controller.list(jwt);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        verify(managementService).list(SUB);
    }

    @Test
    void create_delegatesToManagementService_andReturnsResponse() {
        WatchlistCreateRequest request = new WatchlistCreateRequest("New");
        when(managementService.create(eq(SUB), eq(request))).thenReturn(sampleList());

        ApiResponse<WatchlistResponse> response = controller.create(jwt, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
    }

    @Test
    void rename_delegatesToManagementService_withIdAndPayload() {
        WatchlistRenameRequest request = new WatchlistRenameRequest("Renamed");
        when(managementService.rename(eq(5L), eq(SUB), eq(request))).thenReturn(sampleList());

        ApiResponse<WatchlistResponse> response = controller.rename(jwt, 5L, request);

        assertThat(response.isSuccess()).isTrue();
        verify(managementService).rename(5L, SUB, request);
    }

    @Test
    void delete_invokesManagementService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.delete(jwt, 9L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(managementService).delete(9L, SUB);
    }

    @Test
    void listItems_delegatesToWatchlistService_withSortAndDirection() {
        when(watchlistService.listItems(7L, SUB, WatchlistSortBy.CUSTOM, Sort.Direction.ASC))
                .thenReturn(List.of(sampleItem()));

        ApiResponse<List<WatchlistItemResponse>> response =
                controller.listItems(jwt, 7L, WatchlistSortBy.CUSTOM, Sort.Direction.ASC);

        assertThat(response.getData()).hasSize(1);
        verify(watchlistService).listItems(7L, SUB, WatchlistSortBy.CUSTOM, Sort.Direction.ASC);
    }

    @Test
    void addItem_delegatesToWatchlistService_withRequest() {
        WatchlistItemCreateRequest request = new WatchlistItemCreateRequest(
                MarketType.STOCK, "AAPL", "note", BigDecimal.ZERO);
        when(watchlistService.addToList(7L, SUB, request)).thenReturn(sampleItem());

        ApiResponse<WatchlistItemResponse> response = controller.addItem(jwt, 7L, request);

        assertThat(response.isSuccess()).isTrue();
        verify(watchlistService).addToList(7L, SUB, request);
    }

    @Test
    void reorder_passesItemIdList_toWatchlistService() {
        WatchlistReorderRequest request = new WatchlistReorderRequest(List.of(3L, 2L, 1L));
        when(watchlistService.reorder(7L, SUB, request.itemIds()))
                .thenReturn(List.of(sampleItem()));

        ApiResponse<List<WatchlistItemResponse>> response = controller.reorder(jwt, 7L, request);

        assertThat(response.getData()).hasSize(1);
        verify(watchlistService).reorder(7L, SUB, request.itemIds());
    }

    @Test
    void removeItem_invokesWatchlistService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.removeItem(jwt, 22L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(watchlistService).removeItem(22L, SUB);
    }

    @Test
    void updateItem_delegatesToWatchlistService_withUpdatePayload() {
        WatchlistItemUpdateRequest request = new WatchlistItemUpdateRequest("note", BigDecimal.ZERO);
        when(watchlistService.updateItem(22L, SUB, request)).thenReturn(sampleItem());

        ApiResponse<WatchlistItemResponse> response = controller.updateItem(jwt, 22L, request);

        assertThat(response.getData().id()).isEqualTo(10L);
        verify(watchlistService).updateItem(22L, SUB, request);
    }

    @Test
    void addToDefault_delegatesToWatchlistService() {
        WatchlistItemCreateRequest request = new WatchlistItemCreateRequest(
                MarketType.CRYPTO, "BTC", null, null);
        when(watchlistService.addToDefault(SUB, request)).thenReturn(sampleItem());

        ApiResponse<WatchlistItemResponse> response = controller.addToDefault(jwt, request);

        assertThat(response.isSuccess()).isTrue();
        verify(watchlistService).addToDefault(SUB, request);
    }
}
