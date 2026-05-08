package com.finance.market.crypto.model;

import com.finance.market.core.model.BaseCandle;
import com.finance.market.crypto.model.Crypto;

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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.util.Objects;
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crypto_candles",
    uniqueConstraints = @UniqueConstraint(
        name = "uc_crypto_id_candle_date",
        columnNames = {"crypto_id", "candle_date"}
    ),
    indexes = {
        @Index(name = "idx_crypto_candles_crypto_id", columnList = "crypto_id"),
        @Index(name = "idx_crypto_candles_date", columnList = "candle_date"),
        @Index(name = "idx_crypto_candles_crypto_date", columnList = "crypto_id, candle_date")
    }
)
public class CryptoCandle extends BaseCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crypto_id", referencedColumnName = "id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_crypto_candle_id"))
    @JsonIgnore
    private Crypto crypto;

    @Column(name = "crypto_id", insertable = false, updatable = false, nullable = false)
    private String cryptoId;

    @Column(name = "volume")
    private Long volume;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CryptoCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}