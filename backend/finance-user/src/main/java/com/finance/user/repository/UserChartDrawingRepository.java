package com.finance.user.repository;

import com.finance.user.model.UserChartDrawing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Persistence for {@link UserChartDrawing}, looked up by the (user, tracked asset) pair. */
@Repository
public interface UserChartDrawingRepository extends JpaRepository<UserChartDrawing, Long> {

    Optional<UserChartDrawing> findByUserSubAndTrackedAsset_Id(String userSub, Long trackedAssetId);
}
