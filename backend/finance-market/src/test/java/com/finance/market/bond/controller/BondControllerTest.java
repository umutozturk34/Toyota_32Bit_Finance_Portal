package com.finance.market.bond.controller;


import com.finance.market.bond.dto.response.BondResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.bond.service.BondQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondControllerTest {

    @Mock private BondQueryService bondQueryService;
    @Mock private Translator translator;
    @InjectMocks private BondController controller;

    private BondResponse response(String seriesCode, String type) {
        return new BondResponse(seriesCode, "TRT" + seriesCode,
                new BigDecimal("12.5"), new BigDecimal("25.0"),
                new BigDecimal("100"), LocalDate.of(2025, 1, 1),
                LocalDate.of(2027, 6, 15), null, type, "T.C. Hazine",
                LocalDateTime.now());
    }

    @Test
    void getAllBondsReturnsPaginatedResult() {
        PagedResponse<BondResponse> paged = PagedResponse.of(
                List.of(response("TRT230125T12", "FIXED_COUPON")), 0, 8, 1);
        when(bondQueryService.search(null, null, "simpleYield", "desc", 0, null)).thenReturn(paged);

        var result = controller.getAllBonds(null, null, "simpleYield", "desc", 0, null);

        assertThat(result.getData().content()).hasSize(1);
    }

    @Test
    void getAllBondsWithBondTypeFilters() {
        PagedResponse<BondResponse> paged = PagedResponse.of(
                List.of(response("TRB2025", "DISCOUNTED")), 0, 8, 1);
        when(bondQueryService.search(null, "DISCOUNTED", "simpleYield", "desc", 0, null)).thenReturn(paged);

        var result = controller.getAllBonds(null, "DISCOUNTED", "simpleYield", "desc", 0, null);

        assertThat(result.getData().content()).hasSize(1);
        assertThat(result.getData().content().getFirst().bondType()).isEqualTo("DISCOUNTED");
    }
}
