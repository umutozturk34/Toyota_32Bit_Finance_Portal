package com.finance.market.crypto.model;

import com.finance.market.core.model.BaseCandle;
import com.finance.market.crypto.model.Crypto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.util.Objects;
/**
 * Daily OHLC candle for a crypto coin (native-currency, from Binance klines), unique per
 * (crypto, date). The {@code cryptoId} column is read-only; the {@code crypto} association owns the FK.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crypto_candles",
    uniqueConstraints = @UniqueConstraint(
        name = "uc_crypto_id_candle_date",
        columnNames = {"crypto_id", "candle_date"}
    ),
    indexes = {
        @Index(name = "idx_crypto_candles_crypto_id", columnList = "crypto_id"),
        @Index(name = "idx_crypto_candles_date", columnList = "candle_date"),
        @Index(name = "idx_crypto_candles_crypto_date", columnList = "crypto_id, candle_date")
    }
)
public class CryptoCandle extends BaseCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crypto_id", referencedColumnName = "id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_crypto_candle_id"))
    @JsonIgnore
    private Crypto crypto;

    @Column(name = "crypto_id", insertable = false, updatable = false, nullable = false)
    private String cryptoId;

    @Column(name = "volume")
    private Long volume;
    /**
     * Identity-based equality keyed solely on the generated {@code id}. Transient (unsaved)
     * candles whose {@code id} is {@code null} are never considered equal to any other instance,
     * which avoids two not-yet-persisted candles colliding in collections before they receive
     * database identifiers.
     *
     * @param o the object to compare against
     * @return {@code true} only when {@code o} is a {@code CryptoCandle} with the same non-null id
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CryptoCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    /**
     * Returns a constant, class-derived hash so the value stays stable across an entity's
     * lifecycle even as a transient instance transitions to a persisted one and gains its
     * {@code id}; this keeps the {@code equals}/{@code hashCode} contract intact for entities
     * already living in hash-based collections at the cost of bucketing all candles together.
     *
     * @return a hash code shared by all {@code CryptoCandle} instances
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}