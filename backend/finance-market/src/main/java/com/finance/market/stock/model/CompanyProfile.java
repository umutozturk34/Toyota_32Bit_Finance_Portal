package com.finance.market.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Company künye (profile) for a BIST stock, keyed by its {@code stocks.symbol} (e.g. {@code GARAN.IS}).
 * Reference data enriched from İş Yatırım's company card by a scheduled job and upserted in place, so the
 * stock detail page can show legal name, sector and founding info. The logo is not here — it reuses the
 * existing {@code stocks.image} column.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "company_profile")
public class CompanyProfile {

    @Id
    @Column(name = "symbol", length = 20)
    private String symbol;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "sector")
    private String sector;

    @Column(name = "founded_date")
    private LocalDate foundedDate;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
