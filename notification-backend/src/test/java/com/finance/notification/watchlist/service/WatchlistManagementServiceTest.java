package com.finance.notification.watchlist.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.config.WatchlistManagementProperties;
import com.finance.notification.watchlist.dto.WatchlistCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistRenameRequest;
import com.finance.notification.watchlist.dto.WatchlistResponse;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import com.finance.notification.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistManagementServiceTest {

    @Mock private WatchlistRepository repository;
    @Mock private WatchlistItemRepository itemRepository;
    @Mock private WatchlistManagementProperties properties;
    @Mock private com.finance.common.i18n.Translator translator;
    @Mock private com.finance.notification.user.UserPreferenceCacheService userPreferenceCacheService;

    @InjectMocks
    private WatchlistManagementService service;

    @BeforeEach
    void setUp() {
        lenient().when(userPreferenceCacheService.resolveLocale(anyString())).thenReturn(Locale.forLanguageTag("tr"));
        lenient().when(translator.translate(eq("watchlist.defaultName"), any(Locale.class))).thenReturn("Favoriler");
    }

    private Watchlist favorites() {
        return Watchlist.builder().id(1L).userSub("user-1").name("Favoriler").isDefault(true).build();
    }

    @Test
    void ensureDefault_returnsExistingDefault() {
        Watchlist existing = favorites();
        when(repository.findByUserSubAndIsDefaultTrue("user-1")).thenReturn(Optional.of(existing));

        Watchlist result = service.ensureDefault("user-1");

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any(Watchlist.class));
    }

    @Test
    void ensureDefault_createsFavoritesWhenAbsent() {
        when(repository.findByUserSubAndIsDefaultTrue("user-1")).thenReturn(Optional.empty());
        when(repository.save(any(Watchlist.class))).thenAnswer(inv -> inv.getArgument(0));

        Watchlist result = service.ensureDefault("user-1");

        assertThat(result.getName()).isEqualTo("Favoriler");
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    void create_persistsNewListWithItemCountZero() {
        when(properties.maxPerUser()).thenReturn(20);
        when(repository.countByUserSub("user-1")).thenReturn(1L);
        when(repository.existsByUserSubAndName("user-1", "Crypto")).thenReturn(false);
        when(repository.save(any(Watchlist.class))).thenAnswer(inv -> {
            Watchlist w = inv.getArgument(0);
            w.setId(2L);
            return w;
        });

        WatchlistResponse response = service.create("user-1", new WatchlistCreateRequest("Crypto"));

        assertThat(response.name()).isEqualTo("Crypto");
        assertThat(response.itemCount()).isZero();
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void create_rejectsDuplicateName() {
        when(properties.maxPerUser()).thenReturn(20);
        when(repository.countByUserSub("user-1")).thenReturn(1L);
        when(repository.existsByUserSubAndName("user-1", "Favoriler")).thenReturn(true);

        assertThatThrownBy(() -> service.create("user-1", new WatchlistCreateRequest("Favoriler")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsWhenLimitReached() {
        when(properties.maxPerUser()).thenReturn(20);
        when(repository.countByUserSub("user-1")).thenReturn(20L);

        assertThatThrownBy(() -> service.create("user-1", new WatchlistCreateRequest("Yeni")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rename_updatesNameForOwner() {
        Watchlist target = favorites();
        target.setName("Eski");
        target.setDefault(false);
        when(repository.findById(1L)).thenReturn(Optional.of(target));
        when(repository.existsByUserSubAndName("user-1", "Yeni")).thenReturn(false);
        when(repository.save(any(Watchlist.class))).thenAnswer(inv -> inv.getArgument(0));

        WatchlistResponse result = service.rename(1L, "user-1", new WatchlistRenameRequest("Yeni"));

        assertThat(result.name()).isEqualTo("Yeni");
    }

    @Test
    void rename_throws404ForOtherOwner() {
        when(repository.findById(1L)).thenReturn(Optional.of(favorites()));

        assertThatThrownBy(() -> service.rename(1L, "intruder", new WatchlistRenameRequest("X")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesNonDefaultList() {
        Watchlist target = Watchlist.builder()
                .id(2L).userSub("user-1").name("Crypto").isDefault(false).build();
        when(repository.findById(2L)).thenReturn(Optional.of(target));

        service.delete(2L, "user-1");

        verify(repository).delete(target);
    }

    @Test
    void delete_rejectsDefaultList() {
        when(repository.findById(1L)).thenReturn(Optional.of(favorites()));

        assertThatThrownBy(() -> service.delete(1L, "user-1"))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).delete(any(Watchlist.class));
    }

    @Test
    void list_includesItemCountForEachWatchlist() {
        Watchlist a = favorites();
        Watchlist b = Watchlist.builder().id(2L).userSub("user-1").name("Crypto").isDefault(false).build();
        when(repository.findByUserSubOrderByIsDefaultDescCreatedAtAsc("user-1"))
                .thenReturn(List.of(a, b));
        when(itemRepository.countByWatchlistId(1L)).thenReturn(3L);
        when(itemRepository.countByWatchlistId(2L)).thenReturn(1L);

        List<WatchlistResponse> result = service.list("user-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).itemCount()).isEqualTo(3L);
        assertThat(result.get(1).itemCount()).isEqualTo(1L);
    }

    @Test
    void list_lazyCreatesDefaultWhenUserHasNoLists() {
        when(repository.findByUserSubOrderByIsDefaultDescCreatedAtAsc("user-1"))
                .thenReturn(List.of());
        when(repository.save(any(Watchlist.class)))
                .thenAnswer(inv -> {
                    Watchlist w = inv.getArgument(0);
                    w.setId(99L);
                    return w;
                });

        List<WatchlistResponse> result = service.list("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Favoriler");
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    void create_trimsLeadingTrailingWhitespace() {
        when(properties.maxPerUser()).thenReturn(20);
        when(repository.countByUserSub("user-1")).thenReturn(1L);
        when(repository.existsByUserSubAndName("user-1", "Crypto")).thenReturn(false);
        when(repository.save(any(Watchlist.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("user-1", new WatchlistCreateRequest("  Crypto  "));

        ArgumentCaptor<Watchlist> captor = ArgumentCaptor.forClass(Watchlist.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Crypto");
    }
}
