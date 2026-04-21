package com.finance.backend.model;

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
@Table(name = "commodity_candles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_commodity_code_date",
                columnNames = {"commodity_code", "candle_date"})
    },
    indexes = {
        @Index(name = "idx_commodity_candle_code", columnList = "commodity_code"),
        @Index(name = "idx_commodity_candle_date", columnList = "candle_date"),
        @Index(name = "idx_commodity_candle_code_date", columnList = "commodity_code, candle_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CommodityCandle extends BaseCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commodity_code", referencedColumnName = "commodity_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_commodity_candle_code"))
    @JsonIgnore
    private Commodity commodity;

    @Column(name = "commodity_code", insertable = false, updatable = false, nullable = false)
    private String commodityCode;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommodityCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
