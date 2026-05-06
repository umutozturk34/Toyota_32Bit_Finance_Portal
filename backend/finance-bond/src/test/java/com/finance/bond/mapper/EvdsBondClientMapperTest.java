package com.finance.bond.mapper;
import com.finance.common.service.MarketSnapshotProcessor;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsBondClientMapperTest {

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
