package com.finance.market.macro.client;

import com.finance.common.config.AppProperties;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.macro.config.MacroProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Log4j2
public class EvdsMacroClient extends AbstractEvdsClient {

    private final int defaultBatchSize;

    public EvdsMacroClient(@Qualifier("evdsWebClient") WebClient webClient,
                          AppProperties appProperties,
                          MacroProperties macroProperties) {
        super(webClient, appProperties);
        this.defaultBatchSize = macroProperties.clientDefaultBatchSize();
    }

    public List<EvdsDataResponse> fetchSeriesBatched(List<String> serieCodes,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     int batchSize) {
        if (serieCodes == null || serieCodes.isEmpty()) {
            return Collections.emptyList();
        }
        int effectiveBatch = batchSize > 0 ? batchSize : defaultBatchSize;
        String start = startDate.format(DATE_FMT);
        String end = endDate.format(DATE_FMT);
        List<EvdsDataResponse> responses = new ArrayList<>();
        for (int i = 0; i < serieCodes.size(); i += effectiveBatch) {
            List<String> chunk = serieCodes.subList(i, Math.min(i + effectiveBatch, serieCodes.size()));
            log.debug("Fetching macro batch {}..{} ({} codes) for {}..{}",
                    i, i + chunk.size(), chunk.size(), start, end);
            responses.add(fetchDataRaw(chunk, start, end));
        }
        return responses;
    }
}
