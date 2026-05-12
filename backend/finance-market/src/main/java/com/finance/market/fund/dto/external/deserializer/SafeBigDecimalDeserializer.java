package com.finance.market.fund.dto.external.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

public class SafeBigDecimalDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
        String value = p.getString();
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
