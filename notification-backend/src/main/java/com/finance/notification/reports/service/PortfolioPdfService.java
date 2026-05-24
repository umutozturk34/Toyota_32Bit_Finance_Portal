package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PortfolioPdfModel;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioPdfService {

    private final PortfolioPdfRenderer renderer;

    public byte[] generate(PortfolioPdfRequest request) {
        long start = System.currentTimeMillis();
        try {
            PortfolioPdfModel model = PortfolioPdfModel.fromRequest(request, LocalDateTime.now());
            byte[] pdf = renderer.render(model);
            log.info("Portfolio PDF generated portfolioId={} theme={} locale={} bytes={} durationMs={}",
                    request.portfolio().id(), request.theme(), request.locale(),
                    pdf.length, System.currentTimeMillis() - start);
            return pdf;
        } catch (RuntimeException e) {
            log.error("Portfolio PDF generation failed portfolioId={} theme={} locale={} durationMs={} cause={}",
                    request.portfolio() != null ? request.portfolio().id() : null,
                    request.theme(), request.locale(), System.currentTimeMillis() - start, e.toString(), e);
            throw e;
        }
    }
}
