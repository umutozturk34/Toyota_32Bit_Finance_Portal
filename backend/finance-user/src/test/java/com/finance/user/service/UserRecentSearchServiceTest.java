package com.finance.user.service;

import tools.jackson.databind.ObjectMapper;
import com.finance.user.dto.RecentSearchItem;
import com.finance.user.dto.RecordRecentSearchRequest;
import com.finance.user.model.UserRecentSearch;
import com.finance.user.repository.UserRecentSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRecentSearchServiceTest {

    private static final String USER = "user-1";

    @Mock private UserRecentSearchRepository repository;

    private ObjectMapper objectMapper;
    private UserRecentSearchService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new UserRecentSearchService(repository, objectMapper);
    }

    @Test
    void getItems_returnsEmptyList_whenNoRowExists() {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());

        // Act
        List<RecentSearchItem> items = service.getItems(USER);

        // Assert
        assertThat(items).isEmpty();
    }

    @Test
    void record_prependsNewItem_whenListIsEmpty() {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());
        when(repository.save(any(UserRecentSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest("AAPL", "STOCK", "Apple"));

        // Assert
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().code()).isEqualTo("AAPL");
        assertThat(items.getFirst().type()).isEqualTo("STOCK");
        assertThat(items.getFirst().name()).isEqualTo("Apple");
        assertThat(items.getFirst().searchedAt()).isNotNull();
    }

    @Test
    void record_dedupesByCodeAndType_movingExistingToFront_caseInsensitive() {
        // Arrange
        UserRecentSearch row = rowWith(
                new RecentSearchItem("aapl", "stock", "Apple", Instant.parse("2026-05-01T00:00:00Z")),
                new RecentSearchItem("MSFT", "STOCK", "Microsoft", Instant.parse("2026-05-02T00:00:00Z")));
        when(repository.findById(USER)).thenReturn(Optional.of(row));
        when(repository.save(any(UserRecentSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest("AAPL", "STOCK", "Apple Inc"));

        // Assert
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().code()).isEqualTo("AAPL");
        assertThat(items.getFirst().name()).isEqualTo("Apple Inc");
        assertThat(items.get(1).code()).isEqualTo("MSFT");
        assertThat(items).filteredOn(i -> i.code().equalsIgnoreCase("aapl")).hasSize(1);
    }

    @Test
    void record_capsListToTwenty_droppingOldest() {
        // Arrange
        RecentSearchItem[] existing = new RecentSearchItem[20];
        for (int i = 0; i < 20; i++) {
            existing[i] = new RecentSearchItem("CODE" + i, "STOCK", "Name" + i, Instant.parse("2026-05-01T00:00:00Z"));
        }
        when(repository.findById(USER)).thenReturn(Optional.of(rowWith(existing)));
        when(repository.save(any(UserRecentSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest("NEW", "STOCK", "Newest"));

        // Assert
        assertThat(items).hasSize(20);
        assertThat(items.getFirst().code()).isEqualTo("NEW");
        // The previously-oldest entry (CODE19, last in the array) is dropped once the cap is exceeded.
        assertThat(items).noneMatch(i -> i.code().equals("CODE19"));
    }

    @ParameterizedTest
    @CsvSource({
            "MACRO_INFLATION,inflation",
            "MACRO_RATE,policy-rate",
            "macro_deposit,deposit"
    })
    void record_skipsMacroTypes_withoutPersisting(String type, String code) {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest(code, type, "Macro"));

        // Assert
        assertThat(items).isEmpty();
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "'',STOCK",
            "AAPL,''",
            "'   ',STOCK"
    })
    void record_skipsBlankCodeOrType_withoutPersisting(String code, String type) {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest(code, type, "X"));

        // Assert
        assertThat(items).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void record_persistsTrimmedCodeAndType() {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());
        when(repository.save(any(UserRecentSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<RecentSearchItem> items = service.record(USER, new RecordRecentSearchRequest("  AAPL  ", "  STOCK  ", "Apple"));

        // Assert
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().code()).isEqualTo("AAPL");
        assertThat(items.getFirst().type()).isEqualTo("STOCK");
    }

    @Test
    void clear_deletesTheUserRow() {
        // Act
        service.clear(USER);

        // Assert
        verify(repository, times(1)).deleteById(USER);
    }

    @Test
    void clear_thenGet_returnsEmpty() {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());

        // Act
        service.clear(USER);
        List<RecentSearchItem> items = service.getItems(USER);

        // Assert
        verify(repository).deleteById(USER);
        assertThat(items).isEmpty();
    }

    @Test
    void record_savesSerializedItems_onTheRow() {
        // Arrange
        when(repository.findById(USER)).thenReturn(Optional.empty());
        when(repository.save(any(UserRecentSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.record(USER, new RecordRecentSearchRequest("AAPL", "STOCK", "Apple"));

        // Assert
        ArgumentCaptor<UserRecentSearch> captor = ArgumentCaptor.forClass(UserRecentSearch.class);
        verify(repository).save(captor.capture());
        UserRecentSearch saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER);
        assertThat(saved.getItems().isArray()).isTrue();
        assertThat(saved.getItems().get(0).get("code").asString()).isEqualTo("AAPL");
    }

    private UserRecentSearch rowWith(RecentSearchItem... items) {
        UserRecentSearch row = UserRecentSearch.emptyFor(USER);
        row.setItems(objectMapper.valueToTree(List.of(items)));
        return row;
    }
}
