package com.finance.market.macro.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MacroSlug}: deriving a stable, EVDS-free public slug from a macro indicator's label.
 * Verifies Turkish-letter folding, lowercasing and dash-joining so the raw EVDS code never needs to leak.
 */
class MacroSlugTest {

    @ParameterizedTest
    @CsvSource({
            "Politika Faizi, politika-faizi",
            "TÜFE, tufe",
            "Yİ-ÜFE, yi-ufe",
            "TL Mevduat (1 Ay), tl-mevduat-1-ay",
            "cpiIndex, cpiindex",
            "policyRate, policyrate",
            "Çelik & Gümüş, celik-gumus",
    })
    void slugify_foldsTurkishAndLowercasesAndDashes(String label, String expected) {
        assertThat(MacroSlug.slugify(label)).isEqualTo(expected);
    }

    @Test
    void slugify_returnsEmpty_forNullOrBlank() {
        assertThat(MacroSlug.slugify(null)).isEmpty();
        assertThat(MacroSlug.slugify("   ")).isEmpty();
    }
}
