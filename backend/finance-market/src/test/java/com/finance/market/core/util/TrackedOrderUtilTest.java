package com.finance.market.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedOrderUtilTest {

    private record CodedItem(String code) {}

    @Test
    void sortByTrackedCodes_sortsAccordingToOrderedCodesPosition() {
        List<CodedItem> items = List.of(new CodedItem("C"), new CodedItem("A"), new CodedItem("B"));
        List<String> ordered = List.of("A", "B", "C");

        List<CodedItem> result = TrackedOrderUtil.sortByTrackedCodes(items, ordered, CodedItem::code);

        assertThat(result).extracting(CodedItem::code).containsExactly("A", "B", "C");
    }

    @Test
    void sortByTrackedCodes_placesUnknownCodesAtEnd() {
        List<CodedItem> items = List.of(new CodedItem("Z"), new CodedItem("A"), new CodedItem("B"));
        List<String> ordered = List.of("A", "B");

        List<CodedItem> result = TrackedOrderUtil.sortByTrackedCodes(items, ordered, CodedItem::code);

        assertThat(result).extracting(CodedItem::code).containsExactly("A", "B", "Z");
    }

    @Test
    void sortByTrackedCodes_returnsInputUnchanged_whenItemsNull() {
        List<CodedItem> result = TrackedOrderUtil.sortByTrackedCodes(null, List.of("A"), CodedItem::code);

        assertThat(result).isNull();
    }

    @Test
    void sortByTrackedCodes_returnsInputUnchanged_whenItemsEmpty() {
        List<CodedItem> result = TrackedOrderUtil.sortByTrackedCodes(List.of(), List.of("A"), CodedItem::code);

        assertThat(result).isEmpty();
    }

    @Test
    void sortByTrackedCodes_returnsInputUnchanged_whenOrderedCodesEmpty() {
        List<CodedItem> items = List.of(new CodedItem("B"), new CodedItem("A"));

        List<CodedItem> result = TrackedOrderUtil.sortByTrackedCodes(items, List.of(), CodedItem::code);

        assertThat(result).containsExactlyElementsOf(items);
    }
}
