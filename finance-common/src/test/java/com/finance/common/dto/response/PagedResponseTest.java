package com.finance.common.dto.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseTest {

    @Test
    void ofCalculatesTotalPagesCorrectly() {
        PagedResponse<String> response = PagedResponse.of(List.of("a", "b"), 0, 10, 25);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.content()).containsExactly("a", "b");
    }

    @ParameterizedTest
    @CsvSource({"10, 10, 1", "10, 3, 4", "0, 10, 0", "1, 1, 1", "100, 20, 5", "7, 3, 3"})
    void totalPagesFormula(long totalElements, int size, int expectedPages) {
        PagedResponse<String> response = PagedResponse.of(List.of(), 0, size, totalElements);

        assertThat(response.totalPages()).isEqualTo(expectedPages);
    }

    @Test
    void ofWithZeroSizeReturnsZeroPages() {
        PagedResponse<String> response = PagedResponse.of(List.of(), 0, 0, 10);

        assertThat(response.totalPages()).isZero();
    }

    @Test
    void ofPreservesContentOrder() {
        List<Integer> content = List.of(3, 1, 4, 1, 5);

        PagedResponse<Integer> response = PagedResponse.of(content, 2, 5, 50);

        assertThat(response.content()).containsExactly(3, 1, 4, 1, 5);
        assertThat(response.page()).isEqualTo(2);
    }
}
