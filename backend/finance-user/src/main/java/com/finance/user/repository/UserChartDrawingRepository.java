package com.finance.user.repository;

import com.finance.user.model.UserChartDrawing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link UserChartDrawing}, looked up by the (user, tracked asset) pair. */
public interface UserChartDrawingRepository extends JpaRepository<UserChartDrawing, Long> {

    Optional<UserChartDrawing> findByUserSubAndTrackedAsset_Id(String userSub, Long trackedAssetId);
}
