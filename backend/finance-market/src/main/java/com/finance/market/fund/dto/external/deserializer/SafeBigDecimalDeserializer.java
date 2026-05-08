package com.finance.market.fund.dto.external.deserializer;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

public class SafeBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
