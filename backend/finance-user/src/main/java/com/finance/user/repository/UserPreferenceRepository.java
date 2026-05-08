package com.finance.user.repository;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.user.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
}
