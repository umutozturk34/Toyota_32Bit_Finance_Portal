package com.finance.portfolio.repository;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.portfolio.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserSub(String userSub);

    Optional<Portfolio> findByUserSubAndName(String userSub, String name);

    Optional<Portfolio> findByIdAndUserSub(Long id, String userSub);
}
