package com.finance.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CoinGeckoResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String symbol;
    private String name;
    
    @JsonProperty("current_price")
    private Double currentPrice;
    
    @JsonProperty("market_cap")
    private Long marketCap;
    
    @JsonProperty("market_cap_rank")
    private Integer marketCapRank;
    
    @JsonProperty("total_volume")
    private Long totalVolume;
    
    @JsonProperty("price_change_24h")
    private Double priceChange24h;
    
    @JsonProperty("price_change_percentage_24h")
    private Double priceChangePercentage24h;
}
