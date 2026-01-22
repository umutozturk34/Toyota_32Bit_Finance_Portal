package com.finance.backend.model;

import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base class for all asset entities (Crypto, Stock, etc.)
 * Contains common fields like symbol, name, image, and last update time
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseAsset {
    
    /**
     * Asset symbol (e.g., VSAT, BTC, AAPL)
     */
    private String symbol;
    
    /**
     * Asset full name (e.g., ViaSat Inc, Bitcoin)
     */
    private String name;
    
    /**
     * Logo/image URL
     */
    private String image;
    
    /**
     * Last update timestamp
     */
    private LocalDateTime lastUpdated;
}
