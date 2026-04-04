package com.finance.backend.repository;

import com.finance.backend.model.WalletLedger;
import com.finance.backend.model.LedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {

    boolean existsByWalletIdAndLedgerType(Long walletId, LedgerType ledgerType);
}
