package com.finance.market.forex.model;

import com.finance.market.core.model.BaseCandle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.Objects;
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "forex_candles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_forex_currency_date",
                columnNames = {"currency_code", "candle_date"})
    },
    indexes = {
        @Index(name = "idx_forex_candle_currency", columnList = "currency_code"),
        @Index(name = "idx_forex_candle_date", columnList = "candle_date"),
        @Index(name = "idx_forex_candle_currency_date", columnList = "currency_code, candle_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ForexCandle extends BaseCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", referencedColumnName = "currency_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forex_candle_currency"))
    @JsonIgnore
    private Forex forex;
    @Column(name = "currency_code", insertable = false, updatable = false, nullable = false)
    private String currencyCode;
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForexCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
