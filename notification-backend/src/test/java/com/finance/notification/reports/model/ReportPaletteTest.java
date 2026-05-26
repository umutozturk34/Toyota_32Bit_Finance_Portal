package com.finance.notification.reports.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPaletteTest {

    @Test
    void should_returnLightPalette_when_themeIsLightUppercase() {
        ReportPalette result = ReportPalette.of("LIGHT");

        assertThat(result).isSameAs(ReportPalette.LIGHT);
    }

    @Test
    void should_returnLightPalette_when_themeIsLightLowercase() {
        ReportPalette result = ReportPalette.of("light");

        assertThat(result).isSameAs(ReportPalette.LIGHT);
    }

    @Test
    void should_returnLightPalette_when_themeIsLightMixedCase() {
        ReportPalette result = ReportPalette.of("LiGhT");

        assertThat(result).isSameAs(ReportPalette.LIGHT);
    }

    @Test
    void should_returnDarkPalette_when_themeIsDark() {
        ReportPalette result = ReportPalette.of("DARK");

        assertThat(result).isSameAs(ReportPalette.DARK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "purple", "system", "auto"})
    void should_fallbackToDark_when_themeIsUnknown(String theme) {
        ReportPalette result = ReportPalette.of(theme);

        assertThat(result).isSameAs(ReportPalette.DARK);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_fallbackToDark_when_themeIsNullOrEmpty(String theme) {
        ReportPalette result = ReportPalette.of(theme);

        assertThat(result).isSameAs(ReportPalette.DARK);
    }

    @Test
    void should_exposeDarkPaletteFields_when_accessed() {
        ReportPalette dark = ReportPalette.DARK;

        assertThat(dark.bg()).isEqualTo("#070912");
        assertThat(dark.card()).isEqualTo("#0d111c");
        assertThat(dark.successFg()).isEqualTo("#34d399");
        assertThat(dark.dangerFg()).isEqualTo("#f87171");
    }

    @Test
    void should_exposeLightPaletteFields_when_accessed() {
        ReportPalette light = ReportPalette.LIGHT;

        assertThat(light.bg()).isEqualTo("#f4f5f9");
        assertThat(light.card()).isEqualTo("#ffffff");
        assertThat(light.successFg()).isEqualTo("#059669");
        assertThat(light.dangerFg()).isEqualTo("#dc2626");
    }
}
