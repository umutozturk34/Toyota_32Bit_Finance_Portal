package com.finance.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Crypto snapshot entity - stores current crypto asset information
 * Extends BaseAsset for common asset fields
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cryptos")
public class Crypto extends BaseAsset {
    
    /**
     * CoinGecko ID (primary key, e.g., "bitcoin", "ethereum")
     */
    @Id
    private String id;
    
    // --- Basic Price Data ---
    
    /**
     * Current price (main display price - 42.64)
     */
    private BigDecimal currentPrice;
    
    /**
     * Current price in TRY
     */
    private BigDecimal currentPriceTry;
    
    /**
     * Price change amount (+0.72)
     */
    private BigDecimal changeAmount;
    
    /**
     * Price change percentage (+1.72)
     */
    private BigDecimal changePercent;
    
    // --- Label/Tag Information ---
    
    /**
     * Exchange name (e.g., NASDAQ, BIST, Binance)
     */
    private String exchange;
    
    /**
     * Currency code (e.g., USD, TRY, EUR)
     */
    private String currency;
}
