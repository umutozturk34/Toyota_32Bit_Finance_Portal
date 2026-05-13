package com.finance.app.service.overview;

import com.finance.app.config.OverviewProperties;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverviewWidgetSizeSanitizerTest {

    private JsonMapper mapper;
    private OverviewWidgetSizeSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        OverviewProperties.Limits limits = new OverviewProperties.Limits(10, 3, 50, 8);
        OverviewProperties.WidgetSettings settings = new OverviewProperties.WidgetSettings(
                new OverviewProperties.Size(4, 3),
                new OverviewProperties.Size(2, 2),
                new OverviewProperties.Size(6, 5),
                null);
        Map<WidgetKind, OverviewProperties.WidgetSettings> map = Map.of(
                WidgetKind.NEWS, settings,
                WidgetKind.MOVERS, settings,
                WidgetKind.WATCHLIST, settings,
                WidgetKind.ASSET_CARDS, settings);
        OverviewProperties props = new OverviewProperties(limits, null, map);
        sanitizer = new OverviewWidgetSizeSanitizer(props);
    }

    @Test
    void sanitize_returnsInput_whenNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_returnsInput_whenNotObject() {
        JsonNode input = mapper.readTree("[]");

        assertThat(sanitizer.sanitize(input)).isSameAs(input);
    }

    @Test
    void sanitize_returnsInput_whenSectionsMissing() {
        JsonNode input = mapper.readTree("{}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.has("sections")).isFalse();
    }

    @Test
    void sanitize_keepsNonObjectEntries_inSectionsArray() {
        JsonNode input = mapper.readTree("{\"sections\":[\"str\",null]}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections")).hasSize(2);
    }

    @Test
    void sanitize_clampDefaultsAppliedWhenWidthAndHeightMissing() {
        JsonNode input = mapper.readTree("{\"sections\":[{\"kind\":\"NEWS\"}]}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections").get(0).get("w").asInt()).isEqualTo(4);
        assertThat(result.path("sections").get(0).get("h").asInt()).isEqualTo(3);
    }

    @Test
    void sanitize_raises_whenWidthBelowMinimum() {
        JsonNode input = mapper.readTree("{\"sections\":[{\"kind\":\"NEWS\",\"w\":1,\"h\":3}]}");

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("widgetSizeExceeded");
    }

    @Test
    void sanitize_raises_whenWidthAboveMaximum() {
        JsonNode input = mapper.readTree("{\"sections\":[{\"kind\":\"NEWS\",\"w\":99,\"h\":3}]}");

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sanitize_raises_whenHeightBelowMinimum() {
        JsonNode input = mapper.readTree("{\"sections\":[{\"kind\":\"NEWS\",\"w\":3,\"h\":1}]}");

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sanitize_raises_whenYExceedsLayoutRows() {
        JsonNode input = mapper.readTree(
                "{\"sections\":[{\"kind\":\"NEWS\",\"w\":3,\"h\":3,\"y\":7}]}");

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("layoutRowsExceeded");
    }

    @Test
    void sanitize_raises_whenYIsNegative() {
        JsonNode input = mapper.readTree(
                "{\"sections\":[{\"kind\":\"NEWS\",\"w\":3,\"h\":3,\"y\":-1}]}");

        assertThatThrownBy(() -> sanitizer.sanitize(input))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sanitize_acceptsValidWidget_withFittingDimensions() {
        JsonNode input = mapper.readTree(
                "{\"sections\":[{\"kind\":\"NEWS\",\"w\":4,\"h\":4,\"y\":0}]}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections").get(0).get("w").asInt()).isEqualTo(4);
        assertThat(result.path("sections").get(0).get("h").asInt()).isEqualTo(4);
        assertThat(result.path("sections").get(0).get("y").asInt()).isZero();
    }

    @Test
    void sanitize_leavesUnknownKindUnchanged() {
        JsonNode input = mapper.readTree(
                "{\"sections\":[{\"kind\":\"NOPE\",\"w\":99}]}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections").get(0).get("w").asInt()).isEqualTo(99);
    }

    @Test
    void sanitize_leavesEntryWithoutKindUnchanged() {
        JsonNode input = mapper.readTree("{\"sections\":[{\"w\":99}]}");

        JsonNode result = sanitizer.sanitize(input);

        assertThat(result.path("sections").get(0).get("w").asInt()).isEqualTo(99);
    }
}
