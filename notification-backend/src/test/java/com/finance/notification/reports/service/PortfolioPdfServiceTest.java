package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PortfolioPdfModel;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.ThemeVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPdfServiceTest {

    @Mock private PortfolioPdfRenderer renderer;

    private PortfolioPdfService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioPdfService(renderer);
    }

    @Test
    void generate_forwardsRequestFieldsToRendererAsModel() {
        // Arrange
        PortfolioPdfRequest req = sampleRequest();
        when(renderer.render(any(PortfolioPdfModel.class))).thenReturn(new byte[]{1, 2, 3});

        // Act
        byte[] result = service.generate(req);

        // Assert
        assertThat(result).containsExactly(1, 2, 3);
        ArgumentCaptor<PortfolioPdfModel> captor = ArgumentCaptor.forClass(PortfolioPdfModel.class);
        verify(renderer).render(captor.capture());
        PortfolioPdfModel model = captor.getValue();
        assertThat(model.portfolio().id()).isEqualTo(1L);
        assertThat(model.theme()).isEqualTo(ThemeVariant.DARK);
        assertThat(model.locale().getLanguage()).isEqualTo("tr");
        assertThat(model.currency()).isEqualTo("TRY");
    }

    private PortfolioPdfRequest sampleRequest() {
        return new PortfolioPdfRequest(
                new PortfolioPdfRequest.Portfolio(1L, "x", "u@x.com"),
                new PortfolioPdfRequest.Summary(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(), Map.of(),
                "TRY", ThemeVariant.DARK, "tr");
    }
}
