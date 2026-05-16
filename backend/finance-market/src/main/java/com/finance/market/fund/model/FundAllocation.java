package com.finance.market.fund.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "fund_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_fund_allocation",
                columnNames = {"fund_code", "asset_class", "as_of_date"}),
        indexes = @Index(name = "idx_fund_allocation_code", columnList = "fund_code"))
public class FundAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "fund_code", length = 20, nullable = false)
    private String fundCode;

    @Column(name = "asset_class", length = 16, nullable = false)
    private String assetClass;

    @Column(name = "percentage", precision = 7, scale = 4, nullable = false)
    private BigDecimal percentage;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;
}
