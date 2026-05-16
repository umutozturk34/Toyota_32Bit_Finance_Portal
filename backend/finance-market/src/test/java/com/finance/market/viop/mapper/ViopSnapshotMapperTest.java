package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.dto.external.OneEndeksDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ViopSnapshotMapperTest {

    private final ViopSnapshotMapper mapper = new ViopSnapshotMapper();

    private OneEndeksDto oneEndeks(String symbol, OffsetDateTime updateDate) {
        return new OneEndeksDto(
                updateDate,
                new BigDecimal("35.10"),
                new BigDecimal("35.20"),
                new BigDecimal("34.90"),
                new BigDecimal("35.60"),
                new BigDecimal("35.15"),
                new BigDecimal("35.00"),
                new BigDecimal("100"),
                new BigDecimal("3515.00"),
                new BigDecimal("36.00"),
                new BigDecimal("34.50"),
                new BigDecimal("36.50"),
                new BigDecimal("34.00"),
                new BigDecimal("35.10"),
                new BigDecimal("0.01"),
                new BigDecimal("35.05"),
                new BigDecimal("34.80"),
                new BigDecimal("35.40"),
                new BigDecimal("35.20"),
                new BigDecimal("35.30"),
                new BigDecimal("34.95"),
                new BigDecimal("35.08"),
                new BigDecimal("35.18"),
                new BigDecimal("35.12"),
                new BigDecimal("34.80"),
                new BigDecimal("35.10"),
                new BigDecimal("50"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("100"),
                symbol);
    }

    @Test
    void should_mapAllSnapshotFields_when_fromOneEndeksWithFullDto() {
        OffsetDateTime updateDate = OffsetDateTime.of(2026, 4, 1, 18, 0, 0, 0, ZoneOffset.UTC);
        OneEndeksDto dto = oneEndeks("F_USDTRY0626", updateDate);

        ViopQuoteSnapshot snap = mapper.fromOneEndeks(dto);

        assertThat(snap.symbol()).isEqualTo("F_USDTRY0626");
        assertThat(snap.last()).isEqualByComparingTo("35.15");
        assertThat(snap.bid()).isEqualByComparingTo("35.10");
        assertThat(snap.ask()).isEqualByComparingTo("35.20");
        assertThat(snap.settlement()).isEqualByComparingTo("35.10");
        assertThat(snap.priceStep()).isEqualByComparingTo("0.01");
        assertThat(snap.updatedAt()).isEqualTo(updateDate.toInstant());
    }

    @Test
    void should_useCurrentInstant_when_updateDateIsNull() {
        OneEndeksDto dto = oneEndeks("F_X", null);

        ViopQuoteSnapshot snap = mapper.fromOneEndeks(dto);

        assertThat(snap.updatedAt()).isNotNull();
        assertThat(snap.updatedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    @Test
    void should_deriveDayClose_when_fromHtmlRowProvidesLastAndChange() {
        Instant captured = Instant.parse("2026-04-01T18:00:00Z");

        ViopQuoteSnapshot snap = mapper.fromHtmlRow("F_USDTRY0626",
                new BigDecimal("35.40"),
                new BigDecimal("1.0"),
                new BigDecimal("0.40"),
                new BigDecimal("12345.67"),
                new BigDecimal("100"),
                captured);

        assertThat(snap.symbol()).isEqualTo("F_USDTRY0626");
        assertThat(snap.last()).isEqualByComparingTo("35.40");
        assertThat(snap.dayClose()).isEqualByComparingTo("35.00");
        assertThat(snap.volumeLot()).isEqualByComparingTo("100");
        assertThat(snap.volumeTry()).isEqualByComparingTo("12345.67");
        assertThat(snap.updatedAt()).isEqualTo(captured);
    }

    @Test
    void should_returnNullDayClose_when_fromHtmlRowMissesChangeOrPrice() {
        Instant captured = Instant.now();

        ViopQuoteSnapshot snap = mapper.fromHtmlRow("F_X", null, null, null, null, null, captured);

        assertThat(snap.symbol()).isEqualTo("F_X");
        assertThat(snap.last()).isNull();
        assertThat(snap.dayClose()).isNull();
    }

    @Test
    void should_returnNullDayClose_when_fromHtmlRowChangeIsNullButLastPriceSet() {
        Instant captured = Instant.now();

        ViopQuoteSnapshot snap = mapper.fromHtmlRow("F_X", new BigDecimal("100"),
                null, null, null, null, captured);

        assertThat(snap.dayClose()).isNull();
    }
}
