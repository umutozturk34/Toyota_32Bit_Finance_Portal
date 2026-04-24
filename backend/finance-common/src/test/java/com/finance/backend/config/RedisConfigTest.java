package com.finance.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.model.BaseAsset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConfigTest {

    @Test
    void redisObjectMapper_roundTripBaseAssetSubclass_ignoresDerivedCodeGetterOnRead() throws Exception {
        RedisConfig redisConfig = new RedisConfig();
        ObjectMapper mapper = redisConfig.redisObjectMapper();

        TestAsset original = new TestAsset();
        original.setSymbol("THYAO.IS");
        original.setName("Turkish Airlines");

        byte[] payload = mapper.writeValueAsBytes(original);
        String json = new String(payload);
        assertTrue(json.contains("\"code\""));

        Object deserialized = assertDoesNotThrow(() -> mapper.readValue(payload, Object.class));
        TestAsset restored = mapper.convertValue(deserialized, TestAsset.class);

        assertEquals(original.getSymbol(), restored.getSymbol());
        assertEquals(original.getCode(), restored.getCode());
        assertEquals(original.getName(), restored.getName());
    }

    static class TestAsset extends BaseAsset {
        private String symbol;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String getCode() {
            return symbol;
        }

        @Override
        public void scaleFields(int scale) {
        }
    }
}
