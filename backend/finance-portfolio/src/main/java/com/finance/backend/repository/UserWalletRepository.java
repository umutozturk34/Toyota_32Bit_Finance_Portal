package com.finance.backend.repository;

import com.finance.backend.model.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    Optional<UserWallet> findByPortfolioIdAndCurrency(Long portfolioId, String currency);
}
