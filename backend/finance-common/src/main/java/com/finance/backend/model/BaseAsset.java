package com.finance.backend.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.backend.util.PercentChangeCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
public abstract class BaseAsset {

    protected static BigDecimal scaleValue(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : null;
    }

    protected static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    @Column(name = "name")
    private String name;
    @Column(name = "image")
    private String image;
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    @Column(name = "change_amount", precision = 19, scale = 4)
    private BigDecimal changeAmount;
    @Column(name = "change_percent", precision = 19, scale = 4)
    private BigDecimal changePercent;

    public final void applyChange(BigDecimal current, BigDecimal previous, int scale) {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(current, previous, scale);
        this.changeAmount = result.amount();
        this.changePercent = result.percent();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public abstract String getCode();

    public String resolveDisplayName() {
        return firstNonBlank(getName(), getCode());
    }
}
