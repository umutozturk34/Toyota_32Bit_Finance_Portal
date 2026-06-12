package com.finance.common.repository;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Instrument}, keyed in practice by the ({@link MarketType}, asset code)
 * pair using case-insensitive code matching.
 */
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findByMarketTypeAndAssetCodeIgnoreCase(MarketType marketType, String assetCode);

    boolean existsByMarketTypeAndAssetCodeIgnoreCase(MarketType marketType, String assetCode);

    List<Instrument> findByMarketType(MarketType marketType);
}
