package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundDetailControllerTest {

    @Mock private FundRepository fundRepository;

    private FundDetailController controller;

    @BeforeEach
    void setUp() {
        controller = new FundDetailController(fundRepository);
    }

    @Test
    void should_returnDistinctSubCategories_when_repositoryHasRows() {
        when(fundRepository.findDistinctSubCategories())
                .thenReturn(List.of("Hisse Senedi", "Para Piyasası", "Borçlanma"));

        ApiResponse<List<String>> response = controller.getSubCategories();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly("Hisse Senedi", "Para Piyasası", "Borçlanma");
        verify(fundRepository).findDistinctSubCategories();
    }

    @Test
    void should_returnEmptyList_when_repositoryReturnsNoRows() {
        when(fundRepository.findDistinctSubCategories()).thenReturn(List.of());

        ApiResponse<List<String>> response = controller.getSubCategories();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
        verify(fundRepository).findDistinctSubCategories();
    }
}
