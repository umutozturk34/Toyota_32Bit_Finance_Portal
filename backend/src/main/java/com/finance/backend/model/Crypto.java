package com.finance.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cryptos",
    indexes = {
        @Index(name = "idx_crypto_id", columnList = "id"),
        @Index(name = "idx_crypto_symbol", columnList = "symbol")
    }
)
public class Crypto extends BaseAsset {
    
    @Id
    private String id;

    private String symbol;
    
    private BigDecimal currentPrice;
    
    private BigDecimal currentPriceTry;
    
    private BigDecimal changeAmount;
    
    private BigDecimal changePercent;
    
    private BigDecimal marketCap;
    
    private BigDecimal totalVolume;
    
    private String exchange;
    
    private String currency;
}
