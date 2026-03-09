package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "funds")
public class Fund extends BaseAsset {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "fund_code", length = 20)
    private String fundCode;

    @Column(name = "fund_type", length = 20)
    private String fundType;

    @Column(name = "price", precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "bulletin_price", precision = 19, scale = 4)
    private BigDecimal bulletinPrice;

    @Column(name = "share_count", precision = 19, scale = 2)
    private BigDecimal shareCount;

    @Column(name = "investor_count", precision = 19, scale = 2)
    private BigDecimal investorCount;

    @Column(name = "portfolio_size", precision = 19, scale = 2)
    private BigDecimal portfolioSize;
}
