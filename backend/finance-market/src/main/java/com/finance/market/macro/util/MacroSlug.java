package com.finance.market.macro.util;

import java.util.Locale;

/**
 * Derives a stable, EVDS-free public slug for a macro indicator from its display label, so the raw EVDS code
 * (e.g. {@code TP.BISPOLFAIZ.TUR}) never leaves the backend. Turkish letters are folded to ASCII and the
 * result is lowercased and dash-joined: "Politika Faizi" → {@code politika-faizi}, "TÜFE" → {@code tufe}.
 */
public final class MacroSlug {

    private MacroSlug() {
    }

    public static String slugify(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String folded = label.trim()
                .replace('İ', 'I').replace('ı', 'i')
                .replace('Ş', 'S').replace('ş', 's')
                .replace('Ç', 'C').replace('ç', 'c')
                .replace('Ö', 'O').replace('ö', 'o')
                .replace('Ü', 'U').replace('ü', 'u')
                .replace('Ğ', 'G').replace('ğ', 'g')
                .replace('Â', 'A').replace('â', 'a');
        return folded.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
