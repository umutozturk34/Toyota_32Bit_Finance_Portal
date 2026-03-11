package com.finance.backend.model;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public void scaleOhlc(int scale) {
        this.open = scaleValue(this.open, scale);
        this.high = scaleValue(this.high, scale);
        this.low = scaleValue(this.low, scale);
        this.close = scaleValue(this.close, scale);
    }

    protected static BigDecimal scaleValue(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : null;
    }
}