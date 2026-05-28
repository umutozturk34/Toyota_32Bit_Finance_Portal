package com.finance.market.core.model;
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
/**
 * Shared persistent base for OHLC candles across markets (date plus open/high/low/close),
 * with helpers to scale values and repair high/low bounds.
 */
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

    public void scaleFields(int scale) {
        this.open = scaleValue(this.open, scale);
        this.high = scaleValue(this.high, scale);
        this.low = scaleValue(this.low, scale);
        this.close = scaleValue(this.close, scale);
    }

    /** Scales OHLC then ensures high/low actually bound open/close. */
    public void scaleAndNormalizeOhlc(int scale) {
        scaleFields(scale);
        normalizeHighLow();
    }

    /** Widens high/low so they never fall inside the open-close range (guards bad source data). */
    private void normalizeHighLow() {
        if (open != null && close != null && high != null && low != null) {
            BigDecimal maxOC = open.max(close);
            BigDecimal minOC = open.min(close);
            if (high.compareTo(maxOC) < 0) this.high = maxOC;
            if (low.compareTo(minOC) > 0) this.low = minOC;
        }
    }

    protected static BigDecimal scaleValue(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : null;
    }
}