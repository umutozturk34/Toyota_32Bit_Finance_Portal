package com.finance.backend.filter;

import com.finance.backend.config.AppProperties;
import io.github.bucket4j.Bandwidth;

import java.time.Duration;

public enum Tier {
    ADMIN_TRIGGER {
        @Override
        public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
            return Bandwidth.builder()
                    .capacity(rl.getAdminTriggerLimit())
                    .refillIntervally(rl.getAdminTriggerLimit(), Duration.ofHours(1))
                    .build();
        }

        @Override
        public String errorCode() {
            return "RATE_LIMIT_ADMIN_TRIGGER_EXCEEDED";
        }

        @Override
        public String errorMessage() {
            return "Admin güncelleme tetikleme sınırına ulaştın. Lütfen daha sonra tekrar dene.";
        }
    },
    ADMIN_READ {
        @Override
        public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
            return Bandwidth.builder()
                    .capacity(rl.getAdminReadLimit())
                    .refillIntervally(rl.getAdminReadLimit(), Duration.ofMinutes(1))
                    .build();
        }

        @Override
        public String errorCode() {
            return "RATE_LIMIT_ADMIN_READ_EXCEEDED";
        }

        @Override
        public String errorMessage() {
            return "Admin okuma isteği sınırına ulaştın. Lütfen kısa bir süre sonra tekrar dene.";
        }
    },
    API {
        @Override
        public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
            return Bandwidth.builder()
                    .capacity(rl.getApiLimit())
                    .refillGreedy(rl.getApiLimit(), Duration.ofMinutes(1))
                    .build();
        }

        @Override
        public String errorCode() {
            return "RATE_LIMIT_API_EXCEEDED";
        }

        @Override
        public String errorMessage() {
            return "API istek sınırına ulaştın. Lütfen biraz bekleyip tekrar dene.";
        }
    };

    public abstract Bandwidth toBandwidth(AppProperties.RateLimit rl);

    public abstract String errorCode();

    public abstract String errorMessage();
}
