package com.finance.user.repository;

import com.finance.user.model.UserChartPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link UserChartPreference}, looked up by the (user, tracked asset) pair. */
public interface UserChartPreferenceRepository extends JpaRepository<UserChartPreference, Long> {

    Optional<UserChartPreference> findByUserSubAndTrackedAsset_Id(String userSub, Long trackedAssetId);
}
