package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.finance.app.dto.response.overview.WatchlistData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.app.watchlist.model.OverviewWatchlist;
import com.finance.app.watchlist.model.OverviewWatchlistItem;
import com.finance.app.watchlist.repository.OverviewWatchlistItemRepository;
import com.finance.app.watchlist.repository.OverviewWatchlistRepository;
import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OverviewWatchlistRepository watchlistRepository;
    private OverviewWatchlistItemRepository itemRepository;
    private AssetSnapshotCache assetSnapshotCache;
    private WatchlistWidgetProvider provider;

    @BeforeEach
    void setUp() {
        watchlistRepository = mock(OverviewWatchlistRepository.class);
        itemRepository = mock(OverviewWatchlistItemRepository.class);
        assetSnapshotCache = mock(AssetSnapshotCache.class);
        provider = new WatchlistWidgetProvider(watchlistRepository, itemRepository, assetSnapshotCache, new OverviewDefaults(OverviewPropertiesFixture.standard()));
    }

    private OverviewWatchlist watchlist(Long id, String userSub, String name, boolean def) {
        return new OverviewWatchlist(id, userSub, name, def);
    }

    private OverviewWatchlistItem item(Long id, Long listId, MarketType type, String code, int order) {
        return new OverviewWatchlistItem(id, listId, "user-1", type, code, null, order);
    }

    private WidgetSection sectionFor(String configJson) throws Exception {
        JsonNode node = objectMapper.readTree(configJson);
        return new WidgetSection("watchlist-1", WidgetKind.WATCHLIST, 0, node);
    }

    @Test
    void should_reportWatchlistKind_when_kindQueried() {
        WidgetKind kind = provider.kind();

        assertThat(kind).isEqualTo(WidgetKind.WATCHLIST);
    }

    @Test
    void should_returnFallbackName_when_noListsExist() throws Exception {
        when(watchlistRepository.findByUserSubOrderByIsDefaultDescIdAsc("user-1")).thenReturn(List.of());

        WatchlistData data = provider.fetch("user-1", sectionFor("{}"));

        assertThat(data.watchlistId()).isNull();
        assertThat(data.watchlistName()).isEqualTo("Liste bulunamadı");
        assertThat(data.items()).isEmpty();
    }

    @Test
    void should_resolveExplicitWatchlistId_when_configCarriesId() throws Exception {
        OverviewWatchlist target = watchlist(7L, "user-1", "Custom", false);
        when(watchlistRepository.findByIdAndUserSub(7L, "user-1")).thenReturn(Optional.of(target));
        when(itemRepository.findByWatchlistId(ArgumentMatchers.eq(7L), any())).thenReturn(List.of());

        WatchlistData data = provider.fetch("user-1", sectionFor("{\"watchlistId\":7}"));

        assertThat(data.watchlistId()).isEqualTo(7L);
        assertThat(data.watchlistName()).isEqualTo("Custom");
    }

    @Test
    void should_useDefaultList_when_watchlistIdMissing() throws Exception {
        OverviewWatchlist favs = watchlist(1L, "user-1", "Favoriler", true);
        when(watchlistRepository.findByUserSubOrderByIsDefaultDescIdAsc("user-1")).thenReturn(List.of(favs));
        when(itemRepository.findByWatchlistId(ArgumentMatchers.eq(1L), any())).thenReturn(List.of());

        WatchlistData data = provider.fetch("user-1", sectionFor("{}"));

        assertThat(data.watchlistId()).isEqualTo(1L);
        assertThat(data.watchlistName()).isEqualTo("Favoriler");
    }

    @Test
    void should_returnFallback_when_explicitIdNotOwnedByUser() throws Exception {
        when(watchlistRepository.findByIdAndUserSub(99L, "user-1")).thenReturn(Optional.empty());

        WatchlistData data = provider.fetch("user-1", sectionFor("{\"watchlistId\":99}"));

        assertThat(data.watchlistId()).isNull();
        assertThat(data.watchlistName()).isEqualTo("Liste bulunamadı");
    }

    @Test
    void should_capItemsAtConfiguredLimit_when_listExceeds() throws Exception {
        OverviewWatchlist favs = watchlist(1L, "user-1", "Favoriler", true);
        when(watchlistRepository.findByUserSubOrderByIsDefaultDescIdAsc("user-1")).thenReturn(List.of(favs));
        List<OverviewWatchlistItem> ten = List.of(
                item(1L, 1L, MarketType.STOCK, "A", 0),
                item(2L, 1L, MarketType.STOCK, "B", 1),
                item(3L, 1L, MarketType.STOCK, "C", 2),
                item(4L, 1L, MarketType.STOCK, "D", 3),
                item(5L, 1L, MarketType.STOCK, "E", 4));
        when(itemRepository.findByWatchlistId(ArgumentMatchers.eq(1L), any())).thenReturn(ten);
        when(assetSnapshotCache.findByCodes(any(), any())).thenReturn(Map.of());

        WatchlistData data = provider.fetch("user-1", sectionFor("{\"limit\":3}"));

        assertThat(data.items()).hasSize(3);
        assertThat(data.items()).extracting(WatchlistData.WatchlistRow::assetCode).containsExactly("A", "B", "C");
    }

    @Test
    void should_enrichWithSnapshotPrice_when_cacheReturnsData() throws Exception {
        OverviewWatchlist favs = watchlist(1L, "user-1", "Favoriler", true);
        when(watchlistRepository.findByUserSubOrderByIsDefaultDescIdAsc("user-1")).thenReturn(List.of(favs));
        when(itemRepository.findByWatchlistId(ArgumentMatchers.eq(1L), any()))
                .thenReturn(List.of(item(1L, 1L, MarketType.STOCK, "AKBNK.IS", 0)));
        AssetSnapshot snapshot = new AssetSnapshot(
                "AKBNK.IS", "Akbank", "img.png",
                new BigDecimal("60.50"), null, new BigDecimal("1.25"));
        when(assetSnapshotCache.findByCodes(MarketType.STOCK, Set.of("AKBNK.IS")))
                .thenReturn(Map.of("AKBNK.IS", snapshot));

        WatchlistData data = provider.fetch("user-1", sectionFor("{}"));

        assertThat(data.items()).hasSize(1);
        WatchlistData.WatchlistRow row = data.items().get(0);
        assertThat(row.price()).isEqualByComparingTo("60.50");
        assertThat(row.changePercent()).isEqualByComparingTo("1.25");
        assertThat(row.image()).isEqualTo("img.png");
    }
}
