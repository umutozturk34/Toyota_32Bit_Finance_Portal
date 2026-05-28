package com.finance.market.core.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.common.model.Instrument;
import com.finance.shared.util.PercentChangeCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
/**
 * Shared persistent base for all market asset entities: name/image/last-updated plus change
 * amount/percent, and a lazy link to the cross-module {@link Instrument} identity. Centralizes
 * change computation and price-currency contract so each market subclass adds only its own fields.
 */
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Instrument asset;

    /** Recomputes change amount/percent from current vs. previous price. */
    public final void applyChange(BigDecimal current, BigDecimal previous, int scale) {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(current, previous, scale);
        this.changeAmount = result.amount();
        this.changePercent = result.percent();
    }

    /**
     * Sets change amount/percent, preferring source-provided values when present and otherwise
     * falling back to the computed current-vs-previous change.
     */
    public final void applyChangePreferring(BigDecimal current, BigDecimal previous,
                                            BigDecimal preferredAmount, BigDecimal preferredPercent,
                                            int scale) {
        PercentChangeCalculator.Result computed = PercentChangeCalculator.compute(current, previous, scale);
        this.changeAmount = preferredAmount != null ? scaleValue(preferredAmount, scale) : computed.amount();
        this.changePercent = preferredPercent != null ? scaleValue(preferredPercent, scale) : computed.percent();
    }

    /** Rounds all monetary fields to the given scale; subclass defines which fields. */
    public abstract void scaleFields(int scale);

    /** Stable per-market asset code. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public abstract String getCode();

    /** Display label: the asset name, falling back to its code. */
    public String resolveDisplayName() {
        return firstNonBlank(getName(), getCode());
    }

    /** Current price in TRY. */
    public abstract BigDecimal getPriceTry();

    /** Currency the asset's price is quoted in; TRY unless a market overrides (e.g. VIOP). */
    public String resolvePriceCurrency() {
        return "TRY";
    }
}
