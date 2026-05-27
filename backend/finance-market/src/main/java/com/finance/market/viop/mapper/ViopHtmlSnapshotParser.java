package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopQuoteSnapshot;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Component
public class ViopHtmlSnapshotParser {

    private final ViopSnapshotMapper snapshotMapper;

    public ViopHtmlSnapshotParser(ViopSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    public List<ViopQuoteSnapshot> parse(String html) {
        Document doc = Jsoup.parse(html);
        Instant capturedAt = Instant.now();
        List<ViopQuoteSnapshot> out = new ArrayList<>(550);
        Elements rows = doc.select("table tr");
        for (Element tr : rows) {
            Elements tds = tr.select("td");
            if (tds.size() < 5) continue;
            Element firstTd = tds.first();
            String title = firstTd.attr("title");
            if (title == null || !title.contains("|")) continue;
            String symbol = title.substring(0, title.indexOf('|')).trim();
            if (symbol.isEmpty()) continue;

            BigDecimal last = parseTurkishDecimal(tds.get(1).text());
            BigDecimal changePct = parseTurkishDecimal(tds.get(2).text());
            BigDecimal changeAbs = tds.size() > 3 ? parseTurkishDecimal(tds.get(3).text()) : null;
            BigDecimal volumeTry = tds.size() > 4 ? parseTurkishDecimal(tds.get(4).text()) : null;
            BigDecimal volumeLot = tds.size() > 5 ? parseTurkishDecimal(tds.get(5).text()) : null;

            out.add(snapshotMapper.fromHtmlRow(symbol, last, changePct, changeAbs, volumeTry, volumeLot, capturedAt));
        }
        return out;
    }

    private BigDecimal parseTurkishDecimal(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().replace(".", "").replace(",", ".");
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Skipping unparseable VIOP cell raw={}", raw);
            return null;
        }
    }
}
