package com.finance.market.fund.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Inbound deserialization target for a TEFAS fund-info record. {@link JsonProperty} bridges
 * TEFAS's Turkish field names (e.g. {@code fonKodu}, {@code sonFiyat}) onto English record
 * components, and {@link JsonIgnoreProperties} tolerates extra fields TEFAS may add. Captures
 * the latest snapshot of a fund plus its standing within its category. Monetary values are in TRY.
 *
 * @param fundCode         TEFAS fund code ({@code fonKodu})
 * @param name             fund title ({@code fonUnvan})
 * @param lastPrice        most recent price ({@code sonFiyat})
 * @param dailyReturn      daily return percentage ({@code gunlukGetiri})
 * @param shareCount       number of outstanding shares ({@code payAdet})
 * @param portfolioSize    total portfolio size ({@code portBuyukluk})
 * @param category         fund category name ({@code fonKategori})
 * @param categoryRank     this fund's rank within its category ({@code kategoriDerece})
 * @param categoryFundCount total number of funds in the category ({@code kategoriFonSay})
 * @param investorCount    number of investors ({@code yatirimciSayi})
 * @param marketShare      this fund's market share ({@code pazarPayi})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasFundInfoDto(
        @JsonProperty("fonKodu") String fundCode,
        @JsonProperty("fonUnvan") String name,
        @JsonProperty("sonFiyat") BigDecimal lastPrice,
        @JsonProperty("gunlukGetiri") BigDecimal dailyReturn,
        @JsonProperty("payAdet") BigDecimal shareCount,
        @JsonProperty("portBuyukluk") BigDecimal portfolioSize,
        @JsonProperty("fonKategori") String category,
        @JsonProperty("kategoriDerece") Integer categoryRank,
        @JsonProperty("kategoriFonSay") Integer categoryFundCount,
        @JsonProperty("yatirimciSayi") BigDecimal investorCount,
        @JsonProperty("pazarPayi") BigDecimal marketShare
) {}
