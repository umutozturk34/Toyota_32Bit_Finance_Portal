package com.finance.backend.mapper;

import com.finance.backend.dto.internal.RssArticleData;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.extern.log4j.Log4j2;
import org.jdom2.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Component
@Log4j2
public class RssClientMapper {

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

    public List<RssArticleData> toArticleDataList(SyndFeed feed) {
        return feed.getEntries().stream()
                .filter(entry -> entry.getTitle() != null && entry.getLink() != null)
                .map(this::toArticleData)
                .toList();
    }

    private RssArticleData toArticleData(SyndEntry entry) {
        return new RssArticleData(
                entry.getTitle().trim(),
                entry.getLink().trim(),
                extractDescription(entry),
                extractImageUrl(entry),
                convertDate(entry.getPublishedDate())
        );
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() == null) {
            return null;
        }
        return entry.getDescription().getValue()
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private String extractImageUrl(SyndEntry entry) {
        List<SyndEnclosure> enclosures = entry.getEnclosures();
        if (enclosures != null && !enclosures.isEmpty()) {
            String type = enclosures.getFirst().getType();
            if (type != null && type.startsWith("image")) {
                return enclosures.getFirst().getUrl();
            }
        }

        for (Element element : entry.getForeignMarkup()) {
            if ("thumbnail".equals(element.getName()) || "content".equals(element.getName())) {
                String url = element.getAttributeValue("url");
                if (url != null) {
                    return url;
                }
            }
        }

        return null;
    }

    private LocalDateTime convertDate(Date date) {
        if (date == null) {
            return LocalDateTime.now(ISTANBUL_ZONE);
        }
        return date.toInstant().atZone(ISTANBUL_ZONE).toLocalDateTime();
    }
}
