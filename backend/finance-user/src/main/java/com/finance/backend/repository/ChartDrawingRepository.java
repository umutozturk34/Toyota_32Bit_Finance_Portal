package com.finance.backend.repository;

import com.finance.backend.model.ChartDrawing;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChartDrawingRepository extends MongoRepository<ChartDrawing, String> {

    Optional<ChartDrawing> findByUserSubAndMarketTypeAndAssetCode(String userSub, String marketType, String assetCode);
}
