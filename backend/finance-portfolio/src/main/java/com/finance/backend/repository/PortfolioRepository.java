package com.finance.backend.repository;

import com.finance.backend.model.Portfolio;
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
