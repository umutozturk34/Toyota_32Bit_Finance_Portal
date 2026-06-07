package com.finance.market.bond.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Client-facing snapshot of a bond's defining terms: its identifying codes ({@code seriesCode},
 * {@code isinCode}), coupon and yield metrics, the inflation/auction {@code baseIndex}, the maturity
 * window ({@code maturityStart}..{@code maturityEnd}) with the resolved {@code nextCouponDate}, plus
 * its classified {@code bondType}, {@code issuer}, and the {@code lastUpdated} timestamp. Carries no
 * TRY price because bonds are valued via yield/rate rather than a direct price.
 */
public record BondResponse(
        String seriesCode,
        String isinCode,
        BigDecimal couponRate,
        BigDecimal simpleYield,
        BigDecimal baseIndex,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        LocalDate nextCouponDate,
        String bondType,
        String issuer,
        LocalDateTime lastUpdated
) {}
