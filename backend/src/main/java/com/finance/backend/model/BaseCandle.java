package com.finance.backend.model;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseCandle {
    @Column(name = "candle_date", nullable = false)
    private LocalDateTime candleDate;
    @Column(name = "open", nullable = false, precision = 19, scale = 4)
    private BigDecimal open;
    @Column(name = "high", nullable = false, precision = 19, scale = 4)
    private BigDecimal high;
    @Column(name = "low", nullable = false, precision = 19, scale = 4)
    private BigDecimal low;
    @Column(name = "close", nullable = false, precision = 19, scale = 4)
    private BigDecimal close;
    @Column(name = "volume")
    private Long volume;
}