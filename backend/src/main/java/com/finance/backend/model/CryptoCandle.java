package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Crypto candle/chart data entity
 * Stores OHLC data for crypto price charts
 * Extends BaseCandle for OHLC fields
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "crypto_candles")
public class CryptoCandle extends BaseCandle {
    
    /**
     * Auto-generated ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the crypto asset (Crypto.id)
     */
    private String cryptoId;
}
