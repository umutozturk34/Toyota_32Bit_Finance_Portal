package com.finance.market.forex.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForexSerieMetadataTest {

    @Test
    void should_buildAllFourCodes_when_hasEfektif() {
        ForexSerieMetadata usd = new ForexSerieMetadata("USD", "US Dollar", "ABD Doları", 1, true);

        List<String> codes = usd.seriesCodes();

        assertThat(codes).containsExactly(
                "TP.DK.USD.A.YTL",
                "TP.DK.USD.S.YTL",
                "TP.DK.USD.A.EF.YTL",
                "TP.DK.USD.S.EF.YTL");
    }

    @Test
    void should_buildTwoCodes_when_noEfektif() {
        ForexSerieMetadata bgn = new ForexSerieMetadata("BGN", "Bulgarian Lev", "Bulgar Levası", 1, false);

        List<String> codes = bgn.seriesCodes();

        assertThat(codes).containsExactly("TP.DK.BGN.A.YTL", "TP.DK.BGN.S.YTL");
    }

    @Test
    void should_returnAccessorsForCodes() {
        ForexSerieMetadata jpy = new ForexSerieMetadata("JPY", "Japanese Yen", "Japon Yeni", 100, true);

        assertThat(jpy.dovizBuyingCode()).isEqualTo("TP.DK.JPY.A.YTL");
        assertThat(jpy.dovizSellingCode()).isEqualTo("TP.DK.JPY.S.YTL");
        assertThat(jpy.efektifBuyingCode()).isEqualTo("TP.DK.JPY.A.EF.YTL");
        assertThat(jpy.efektifSellingCode()).isEqualTo("TP.DK.JPY.S.EF.YTL");
    }
}
