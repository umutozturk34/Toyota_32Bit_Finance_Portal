package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "crypto_candles", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"crypto_id", "candle_date"}),
    indexes = {
        @Index(name = "idx_crypto_candle_id", columnList = "crypto_id"),
        @Index(name = "idx_crypto_candle_date", columnList = "candle_date"),
        @Index(name = "idx_crypto_candle_id_date", columnList = "crypto_id, candle_date")
    }
)
public class CryptoCandle extends BaseCandle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String cryptoId;
}
