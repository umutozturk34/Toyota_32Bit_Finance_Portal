package com.finance.market.stock.repository;

import com.finance.market.stock.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link CompanyProfile} reference rows, keyed by stock symbol. */
public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, String> {
}
