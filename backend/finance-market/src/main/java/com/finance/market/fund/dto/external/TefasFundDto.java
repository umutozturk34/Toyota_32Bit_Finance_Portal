package com.finance.market.fund.dto.external;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single dated price observation for a TEFAS mutual fund, used to carry historical/daily
 * fund data within the module after parsing from the TEFAS source. Each instance represents
 * the fund's state on {@code date}: its net asset value per share ({@code price}), the official
 * bulletin price, and size metrics. Monetary values are in TRY.
 *
 * @param fundCode      TEFAS fund code (identifier, e.g. {@code AFA})
 * @param name          fund title
 * @param date          valuation date/time of this observation
 * @param price         net asset value per share
 * @param bulletinPrice officially published bulletin price for the day
 * @param shareCount    number of outstanding shares
 * @param investorCount number of investors holding the fund
 * @param portfolioSize total portfolio (fund) size
 */
public record TefasFundDto(
        String fundCode,
        String name,
        LocalDateTime date,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
