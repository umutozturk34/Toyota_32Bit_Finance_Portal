package com.finance.market.commodity.model;

import com.finance.market.core.model.BaseCandle;
import com.finance.market.commodity.model.Commodity;

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
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "commodity_candles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_commodity_code_date",
                columnNames = {"commodity_code", "candle_date"})
    },
    indexes = {
        @Index(name = "idx_commodity_candle_code", columnList = "commodity_code"),
        @Index(name = "idx_commodity_candle_date", columnList = "candle_date"),
        @Index(name = "idx_commodity_candle_code_date", columnList = "commodity_code, candle_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CommodityCandle extends BaseCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commodity_code", referencedColumnName = "commodity_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_commodity_candle_code"))
    @JsonIgnore
    private Commodity commodity;

    @Column(name = "commodity_code", insertable = false, updatable = false, nullable = false)
    private String commodityCode;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommodityCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
