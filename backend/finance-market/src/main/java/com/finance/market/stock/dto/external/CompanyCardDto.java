package com.finance.market.stock.dto.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed contents of an İş Yatırım company-card page ({@code sirket-karti.aspx?hisse=<ticker>}): the
 * company künye and the weighted list of BIST indices the stock belongs to. The company logo is NOT on
 * that page (only İş Yatırım's own brand mark), so it is sourced separately and absent here.
 *
 * @param legalName     registered company name (Ünvanı)
 * @param sector        business activity / sector (Faal Alanı)
 * @param foundedDate   incorporation date (Kuruluş), null when unparseable
 * @param city          province parsed from the address (Adres), null when absent
 * @param indexWeights  the indices the stock is a member of, with its weight in each
 */
public record CompanyCardDto(
        String legalName,
        String sector,
        LocalDate foundedDate,
        String city,
        List<IndexWeight> indexWeights) {

    /** A single index membership: the BIST index code (e.g. {@code XU030}) and the stock's weight in it. */
    public record IndexWeight(String indexCode, BigDecimal weight) {
    }
}
