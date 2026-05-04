package com.finance.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedOrderUtilTest {

    @Test
    void sortsItemsByTrackedCodeOrder() {
        List<String> items = new ArrayList<>(List.of("THYAO.IS", "GARAN.IS", "ASELS.IS"));
        List<String> orderedCodes = List.of("ASELS.IS", "THYAO.IS", "GARAN.IS");

        List<String> sorted = TrackedOrderUtil.sortByTrackedCodes(items, orderedCodes, s -> s);

        assertThat(sorted).containsExactly("ASELS.IS", "THYAO.IS", "GARAN.IS");
    }

    @Test
    void unmappedItemsGoToEnd() {
        List<String> items = new ArrayList<>(List.of("UNKNOWN", "THYAO.IS", "ASELS.IS"));
        List<String> orderedCodes = List.of("ASELS.IS", "THYAO.IS");

        List<String> sorted = TrackedOrderUtil.sortByTrackedCodes(items, orderedCodes, s -> s);

        assertThat(sorted).containsExactly("ASELS.IS", "THYAO.IS", "UNKNOWN");
    }

    @Test
    void nullItemsListReturnsNull() {
        List<String> result = TrackedOrderUtil.sortByTrackedCodes(null, List.of("A"), s -> s);

        assertThat(result).isNull();
    }

    @Test
    void emptyItemsListReturnsEmpty() {
        List<String> result = TrackedOrderUtil.sortByTrackedCodes(List.of(), List.of("A"), s -> s);

        assertThat(result).isEmpty();
    }

    @Test
    void nullOrderedCodesReturnsOriginal() {
        List<String> items = List.of("A", "B");

        List<String> result = TrackedOrderUtil.sortByTrackedCodes(items, null, s -> s);

        assertThat(result).containsExactly("A", "B");
    }

    @Test
    void emptyOrderedCodesReturnsOriginal() {
        List<String> items = List.of("A", "B");

        List<String> result = TrackedOrderUtil.sortByTrackedCodes(items, List.of(), s -> s);

        assertThat(result).containsExactly("A", "B");
    }

    @Test
    void worksWithCustomCodeExtractor() {
        record Asset(String code, String name) {}
        List<Asset> items = new ArrayList<>(List.of(
                new Asset("BTC", "Bitcoin"),
                new Asset("ETH", "Ethereum"),
                new Asset("SOL", "Solana")));
        List<String> orderedCodes = List.of("SOL", "BTC", "ETH");

        List<Asset> sorted = TrackedOrderUtil.sortByTrackedCodes(items, orderedCodes, Asset::code);

        assertThat(sorted).extracting(Asset::code).containsExactly("SOL", "BTC", "ETH");
    }
}
