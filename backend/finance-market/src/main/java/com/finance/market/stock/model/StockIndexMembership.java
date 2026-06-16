package com.finance.market.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Membership of a tradable stock in a BIST index, with the stock's weight in that index. The set is
 * reconciled on each enrichment run (rows missing from the latest İş Yatırım fetch are deleted), so it
 * always mirrors the source rather than accumulating stale entries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_index_membership")
public class StockIndexMembership {

    @EmbeddedId
    private Key id;

    @Column(name = "weight", precision = 9, scale = 4)
    private BigDecimal weight;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Composite key: the tradable stock symbol ({@code GARAN.IS}) and the bare index code ({@code XU030}). */
    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "stock_symbol", length = 20)
        private String stockSymbol;

        @Column(name = "index_code", length = 20)
        private String indexCode;
    }
}
