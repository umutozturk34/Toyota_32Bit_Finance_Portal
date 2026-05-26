package com.finance.market.bond.mapper;


import com.finance.market.bond.dto.external.BondRateItemDto;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsBondClientMapperTest {

    private EvdsBondClientMapper mapper;

    @BeforeEach
    void initMapper() {
        mapper = new EvdsBondClientMapper();
    }

    private BondSerieDto serie(String isin) {
        return new BondSerieDto(isin, "TP." + isin + ".TL.PY", "5Y TRT",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
    }

    private Map<String, Object> itemWith(String key, Object value, String date) {
        Map<String, Object> item = new HashMap<>();
        item.put(key, value);
        item.put("Tarih", date);
        return item;
    }

    @Test
    void toSnapshotDtos_returnsEmpty_whenResponseHasNoItems() {
        EvdsDataResponse response = new EvdsDataResponse(0, null);

        List<BondSnapshotDto> result = mapper.toSnapshotDtos(List.of(serie("TRT271235T17")), response);

        assertThat(result).isEmpty();
    }

    @Test
    void toSnapshotDtos_returnsEmpty_whenItemsListEmpty() {
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());

        List<BondSnapshotDto> result = mapper.toSnapshotDtos(List.of(serie("TRT271235T17")), response);

        assertThat(result).isEmpty();
    }

    @Test
    void toSnapshotDtos_extractsPriceAndCouponRate_fromLatestNonNullItem() {
        BondSerieDto s = serie("TRT271235T17");
        Map<String, Object> item = new HashMap<>();
        item.put("TP_TRT271235T17_TL_PY", "98.50");
        item.put("TP_TRT271235T17_ORAN", "10.5");
        item.put("Tarih", "12-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(item));

        List<BondSnapshotDto> result = mapper.toSnapshotDtos(List.of(s), response);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cleanPrice()).isEqualByComparingTo(new BigDecimal("98.50"));
        assertThat(result.get(0).couponRate()).isEqualByComparingTo(new BigDecimal("10.5"));
    }

    @Test
    void toSnapshotDtos_skipsBondsWithBothFieldsNull() {
        BondSerieDto a = serie("TRT271235T17");
        BondSerieDto b = serie("TRT281236T18");
        Map<String, Object> item = new HashMap<>();
        item.put("TP_TRT271235T17_TL_PY", "98.50");
        item.put("Tarih", "12-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(item));

        List<BondSnapshotDto> result = mapper.toSnapshotDtos(List.of(a, b), response);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isinCode()).isEqualTo("TRT271235T17");
    }

    @Test
    void toSnapshotDtos_walksBackThroughItems_toFindNonNullValue() {
        BondSerieDto s = serie("TRT271235T17");
        Map<String, Object> emptyLast = new HashMap<>();
        emptyLast.put("Tarih", "12-05-2026");
        Map<String, Object> populated = new HashMap<>();
        populated.put("TP_TRT271235T17_TL_PY", "98.50");
        populated.put("Tarih", "11-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(populated, emptyLast));

        List<BondSnapshotDto> result = mapper.toSnapshotDtos(List.of(s), response);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cleanPrice()).isEqualByComparingTo(new BigDecimal("98.50"));
    }

    @Test
    void toRateItemDtos_returnsEmpty_whenItemsNull() {
        EvdsDataResponse response = new EvdsDataResponse(0, null);

        List<BondRateItemDto> result = mapper.toRateItemDtos(response, "TP.TRT271235T17.ORAN", null);

        assertThat(result).isEmpty();
    }

    @Test
    void toRateItemDtos_extractsRatesAndDates_fromItems() {
        Map<String, Object> a = itemWith("TP_TRT271235T17_ORAN", "10.5", "01-05-2026");
        Map<String, Object> b = itemWith("TP_TRT271235T17_ORAN", "10.7", "02-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(a, b));

        List<BondRateItemDto> result = mapper.toRateItemDtos(response, "TP.TRT271235T17.ORAN", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).couponRate()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(result.get(0).rateDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void toRateItemDtos_skipsItemsWithMissingRateOrDate() {
        Map<String, Object> withDateNoRate = itemWith("Other", "x", "01-05-2026");
        Map<String, Object> withRateNoDate = new HashMap<>();
        withRateNoDate.put("TP_TRT271235T17_ORAN", "10.5");
        Map<String, Object> valid = itemWith("TP_TRT271235T17_ORAN", "10.7", "02-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(3, List.of(withDateNoRate, withRateNoDate, valid));

        List<BondRateItemDto> result = mapper.toRateItemDtos(response, "TP.TRT271235T17.ORAN", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void toRateItemDtos_skipsItem_whenDateUnparseable() {
        Map<String, Object> bad = itemWith("TP_TRT271235T17_ORAN", "10.5", "not-a-date");
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(bad));

        List<BondRateItemDto> result = mapper.toRateItemDtos(response, "TP.TRT271235T17.ORAN", null);

        assertThat(result).isEmpty();
    }

    @Test
    void toRateItemDtos_extractsPrice_whenPriceCodeProvided() {
        Map<String, Object> item = new HashMap<>();
        item.put("TP_TRT271235T17_ORAN", "10.5");
        item.put("TP_TRT271235T17_TL_PY", "98.7500");
        item.put("Tarih", "01-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(item));

        List<BondRateItemDto> result = mapper.toRateItemDtos(response,
                "TP.TRT271235T17.ORAN", "TP.TRT271235T17.TL.PY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).couponRate()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(result.get(0).price()).isEqualByComparingTo(new BigDecimal("98.7500"));
    }

    @Test
    void toRateItemDtos_leavesPriceNull_whenPriceCodeProvidedButValueMissing() {
        Map<String, Object> item = itemWith("TP_TRT271235T17_ORAN", "10.5", "01-05-2026");
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(item));

        List<BondRateItemDto> result = mapper.toRateItemDtos(response,
                "TP.TRT271235T17.ORAN", "TP.TRT271235T17.TL.PY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).price()).isNull();
    }

    @Test
    void longValuePreservesFullPrecision() {
        long large = 9_999_999_999_999_999L;
        Map<String, Object> item = Map.of("k", large);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isEqualTo(new BigDecimal(large));
        assertThat(result.toPlainString()).isEqualTo("9999999999999999");
    }

    @Test
    void bigIntegerValuePreservesFullPrecision() {
        BigInteger huge = new BigInteger("12345678901234567890123456789");
        Map<String, Object> item = Map.of("k", huge);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isEqualTo(new BigDecimal(huge));
    }

    @Test
    void bigDecimalValueIsReturnedDirectly() {
        BigDecimal exact = new BigDecimal("12.345678901234567890");
        Map<String, Object> item = Map.of("k", exact);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isSameAs(exact);
    }

    @Test
    void doubleValueIsParsedViaToStringNotLossyLongConversion() {
        double value = 12.34;
        Map<String, Object> item = Map.of("k", value);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isEqualTo(new BigDecimal("12.34"));
    }

    @Test
    void integerValueIsParsedExactly() {
        Map<String, Object> item = Map.of("k", 42);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isEqualTo(new BigDecimal("42"));
    }

    @Test
    void stringNumericValueIsParsed() {
        Map<String, Object> item = Map.of("k", "  12.3456  ");

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isEqualTo(new BigDecimal("12.3456"));
    }

    @Test
    void missingKeyReturnsNull() {
        Map<String, Object> item = Map.of("other", "5.0");

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isNull();
    }

    @Test
    void nullMapValueReturnsNull() {
        Map<String, Object> item = new HashMap<>();
        item.put("k", null);

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isNull();
    }

    @Test
    void blankStringReturnsNull() {
        Map<String, Object> item = Map.of("k", "   ");

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isNull();
    }

    @Test
    void unparseableStringReturnsNull() {
        Map<String, Object> item = Map.of("k", "not-a-number");

        BigDecimal result = EvdsBondClientMapper.extractBigDecimal(item, "k");

        assertThat(result).isNull();
    }
}
