package com.finance.market.bank.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "bank_exchange_rates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bank_rate",
                columnNames = {"source", "bank_code", "currency_code"}),
        indexes = {
                @Index(name = "idx_bank_rate_currency", columnList = "currency_code"),
                @Index(name = "idx_bank_rate_bank", columnList = "bank_code"),
                @Index(name = "idx_bank_rate_asset_kind", columnList = "asset_kind")
        })
/**
 * One bank's buy/sell rate for a currency or gold product, unique per (source, bank, currency) and
 * overwritten in place on each refresh ({@code capturedAt} marks the latest read).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BankExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "bank_code", nullable = false, length = 64)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 128)
    private String bankName;

    @Column(name = "bank_logo_url", length = 512)
    private String bankLogoUrl;

    @Column(name = "currency_code", nullable = false, length = 32)
    private String currencyCode;

    @Column(name = "currency_name", length = 64)
    private String currencyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 16)
    private BankRateAssetKind assetKind;

    @Column(name = "buy_rate", precision = 19, scale = 4)
    private BigDecimal buyRate;

    @Column(name = "sell_rate", precision = 19, scale = 4)
    private BigDecimal sellRate;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
