package com.finance.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "tracked_assets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_tracked_assets_type_code", columnNames = {"asset_type", "asset_code"})
        },
        indexes = {
                @Index(name = "idx_tracked_assets_type_sort", columnList = "asset_type,sort_order")
        }
)
public class TrackedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private TrackedAssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Instrument asset;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "binance_symbol", length = 100)
    private String binanceSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_segment", length = 30)
    private StockSegment stockSegment;

    @Column(name = "index_asset", nullable = false)
    private boolean indexAsset;

    @Column(name = "compare_only", nullable = false)
    private boolean compareOnly;

    @Column(name = "enabled", nullable = false)
    @lombok.Builder.Default
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
