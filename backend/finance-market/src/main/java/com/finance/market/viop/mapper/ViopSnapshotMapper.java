package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.dto.external.OneEndeksDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class ViopSnapshotMapper {

    public ViopQuoteSnapshot fromOneEndeks(OneEndeksDto dto) {
        return new ViopQuoteSnapshot(
                dto.symbol(),
                toInstant(dto.updateDate()),
                dto.bid(),
                dto.ask(),
                dto.last(),
                dto.dayClose(),
                dto.open(),
                dto.high(),
                dto.low(),
                dto.quantity(),
                dto.volume(),
                dto.settlement(),
                dto.preSettlement(),
                dto.limitUp(),
                dto.limitDown(),
                dto.weekHigh(),
                dto.weekLow(),
                dto.weekClose(),
                dto.monthHigh(),
                dto.monthLow(),
                dto.monthClose(),
                dto.yearClose(),
                dto.prevYearClose(),
                dto.initialMargin(),
                dto.priceStep()
        );
    }

    public ViopQuoteSnapshot fromHtmlRow(String symbol, BigDecimal lastPrice,
                                         BigDecimal changePct, BigDecimal changeAbs,
                                         BigDecimal volumeTry, BigDecimal volumeLot,
                                         Instant capturedAt) {
        BigDecimal previousClose = (lastPrice != null && changeAbs != null) ? lastPrice.subtract(changeAbs) : null;
        return new ViopQuoteSnapshot(
                symbol,
                capturedAt,
                null,
                null,
                lastPrice,
                previousClose,
                null,
                null,
                null,
                volumeLot,
                volumeTry,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt == null ? Instant.now() : odt.toInstant();
    }
}
