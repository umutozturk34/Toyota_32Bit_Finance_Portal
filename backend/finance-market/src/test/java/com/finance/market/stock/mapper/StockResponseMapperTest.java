package com.finance.market.stock.mapper;

import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockIndexMembership;
import com.finance.shared.dto.response.StockMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StockResponseMapperTest {

    private final StockResponseMapper mapper = new StockResponseMapperImpl();

    private static StockIndexMembership member(String symbol, String weight) {
        return StockIndexMembership.builder()
                .id(new StockIndexMembership.Key(symbol, "XU030"))
                .weight(new BigDecimal(weight))
                .build();
    }

    @Test
    void should_populateConstituentNameFromMap_when_buildingDetailMetadata() {
        // Arrange
        Stock index = Stock.builder().symbol("XU030.IS").build();
        StockIndexMembership member = member("THYAO.IS", "9.5");
        Map<String, String> names = Map.of("THYAO.IS", "Test Airlines Inc");

        // Act
        StockMetadata metadata = mapper.buildDetailMetadata(index, null, List.of(), List.of(member), names);

        // Assert
        assertThat(metadata.constituents()).singleElement().satisfies(c -> {
            assertThat(c.stockSymbol()).isEqualTo("THYAO.IS");
            assertThat(c.stockName()).isEqualTo("Test Airlines Inc");
            assertThat(c.weight()).isEqualByComparingTo("9.5");
        });
    }

    @Test
    void should_leaveConstituentNameNull_when_symbolMissingFromNames() {
        // Arrange — the member has not been enriched with a name yet, so the names map has no entry for it.
        Stock index = Stock.builder().symbol("XU030.IS").build();
        StockIndexMembership member = member("XXXX.IS", "1.0");

        // Act
        StockMetadata metadata = mapper.buildDetailMetadata(index, null, List.of(), List.of(member), Map.of());

        // Assert — null name lets the client fall back to the bare symbol for the tooltip.
        assertThat(metadata.constituents()).singleElement()
                .satisfies(c -> assertThat(c.stockName()).isNull());
    }
}
