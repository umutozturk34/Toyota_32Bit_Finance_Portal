package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverviewWidgetDedupSanitizerTest {

    private JsonMapper mapper;
    private OverviewWidgetDedupSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        OverviewProperties.Limits limits = new OverviewProperties.Limits(10, 3, 50, 8);
        OverviewProperties props = new OverviewProperties(limits, null, Map.of());
        sanitizer = new OverviewWidgetDedupSanitizer(props);
    }

    @Test
    void sanitize_returnsInput_whenNull() {
        JsonNode result = sanitizer.sanitize(null);

        assertThat(result).isNull();
    }

    @Test
    void sanitize_returnsInput_whenNotObject() {
        JsonNode input = mapper.readTree("[]");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result).isSameAs(input);
    }

    @Test
    void sanitize_returnsInput_whenSectionsMissing() {
        JsonNode input = mapper.readTree("{}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.has("sections")).isFalse();
    }

    @Test
    void sanitize_returnsInput_whenSectionsNotArray() {
        JsonNode input = mapper.readTree("{\"sections\":\"oops\"}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections").asString()).isEqualTo("oops");
    }

    @Test
    void sanitize_raisesBusinessException_whenVisibleWidgetsExceedLimit() {
        StringBuilder sb = new StringBuilder("{\"sections\":[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"kind\":\"NEWS\",\"sectionId\":\"sec-").append(i).append("\"}");
        }
        sb.append("]}");
        JsonNode input = mapper.readTree(sb.toString());

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maxWidgetsExceeded");
    }

    @Test
    void sanitize_raisesBusinessException_whenAssetCardsExceedLimit() {
        StringBuilder sb = new StringBuilder("{\"sections\":[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"kind\":\"ASSET_CARDS\",\"sectionId\":\"sec-").append(i).append("\"}");
        }
        sb.append("]}");
        JsonNode input = mapper.readTree(sb.toString());

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maxAssetCardsExceeded");
    }

    @Test
    void sanitize_skipsHiddenWidgets_fromLimitCheck() {
        StringBuilder sb = new StringBuilder("{\"sections\":[");
        for (int i = 0; i < 15; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"kind\":\"NEWS\",\"sectionId\":\"sec-").append(i)
                    .append("\",\"visible\":false}");
        }
        sb.append("]}");
        JsonNode input = mapper.readTree(sb.toString());

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).isEmpty();
    }

    @Test
    void sanitize_dedupsWatchlistWidgets_byWatchlistId() {
        String json = "{\"sections\":[" +
                "{\"kind\":\"WATCHLIST\",\"config\":{\"watchlistId\":\"wl-1\"}}," +
                "{\"kind\":\"WATCHLIST\",\"config\":{\"watchlistId\":\"wl-1\"}}," +
                "{\"kind\":\"WATCHLIST\",\"config\":{\"watchlistId\":\"wl-2\"}}]}";
        JsonNode input = mapper.readTree(json);

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(2);
    }

    @Test
    void sanitize_dedupsMoversWidgets_byMarket() {
        String json = "{\"sections\":[" +
                "{\"kind\":\"MOVERS\",\"config\":{\"market\":\"STOCK\"}}," +
                "{\"kind\":\"MOVERS\",\"config\":{\"market\":\"STOCK\"}}," +
                "{\"kind\":\"MOVERS\",\"config\":{\"market\":\"CRYPTO\"}}]}";
        JsonNode input = mapper.readTree(json);

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(2);
    }

    @Test
    void sanitize_keepsAssetCardsWidgets_byUniqueSectionId() {
        String json = "{\"sections\":[" +
                "{\"kind\":\"ASSET_CARDS\",\"sectionId\":\"a\"}," +
                "{\"kind\":\"ASSET_CARDS\",\"sectionId\":\"a\"}," +
                "{\"kind\":\"ASSET_CARDS\",\"sectionId\":\"b\"}]}";
        JsonNode input = mapper.readTree(json);

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(2);
    }

    @Test
    void sanitize_dedupsOtherKindsByName() {
        String json = "{\"sections\":[" +
                "{\"kind\":\"NEWS\"},{\"kind\":\"NEWS\"},{\"kind\":\"PORTFOLIO\"}]}";
        JsonNode input = mapper.readTree(json);

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(2);
    }

    @Test
    void sanitize_dedupsWatchlist_whenIdMissing_underDefault() {
        String json = "{\"sections\":[" +
                "{\"kind\":\"WATCHLIST\",\"config\":{}}," +
                "{\"kind\":\"WATCHLIST\",\"config\":{}}]}";
        JsonNode input = mapper.readTree(json);

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(1);
    }
}
