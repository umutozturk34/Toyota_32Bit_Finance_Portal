package com.finance.market.viop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Daily close for a VIOP contract. İş Yatırım's {@code IndexHistoricalAll} returns close-only
 * data ({@code [ts_ms, close]}), so the table mirrors that — no OHLC, no volume.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "viop_candles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_viop_candle_symbol_date",
                        columnNames = {"symbol", "candle_date"})
        },
        indexes = {
                @Index(name = "idx_viop_candle_symbol", columnList = "symbol"),
                @Index(name = "idx_viop_candle_date", columnList = "candle_date"),
                @Index(name = "idx_viop_candle_symbol_date", columnList = "symbol, candle_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViopCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", nullable = false,
            foreignKey = @ForeignKey(name = "fk_viop_candle_symbol"))
    @JsonIgnore
    private ViopContract contract;

    @Column(name = "symbol", insertable = false, updatable = false, nullable = false)
    private String symbol;

    @Column(name = "candle_date", nullable = false)
    private LocalDateTime candleDate;

    @Column(name = "close", nullable = false, precision = 19, scale = 4)
    private BigDecimal close;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViopCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
