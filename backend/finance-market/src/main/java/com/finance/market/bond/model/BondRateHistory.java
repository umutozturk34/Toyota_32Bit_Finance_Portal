package com.finance.market.bond.model;
import com.finance.market.bond.model.Bond;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bond_rate_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_bond_rate_isin_date",
                columnNames = {"isin_code", "rate_date"}),
        indexes = {
                @Index(name = "idx_bond_rate_isin", columnList = "isin_code"),
                @Index(name = "idx_bond_rate_date", columnList = "rate_date"),
                @Index(name = "idx_bond_rate_isin_date", columnList = "isin_code, rate_date")
        })
public class BondRateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isin_code", referencedColumnName = "isin_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_bond_rate_isin"))
    @JsonIgnore
    private Bond bond;

    @Column(name = "isin_code", insertable = false, updatable = false, nullable = false, length = 50)
    private String isinCode;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "coupon_rate", precision = 10, scale = 4)
    private BigDecimal couponRate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BondRateHistory that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
