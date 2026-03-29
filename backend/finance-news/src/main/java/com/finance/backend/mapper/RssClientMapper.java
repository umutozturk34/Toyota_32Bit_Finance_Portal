package com.finance.backend.mapper;

import com.finance.backend.dto.internal.RssArticleData;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.finance.backend.util.NewsTextUtils;
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
                NewsTextUtils.decodeHtml(entry.getTitle().trim()),
                entry.getLink().trim(),
                extractDescription(entry),
                extractContent(entry),
                extractImageUrl(entry),
                extractGuid(entry),
                convertDate(entry.getPublishedDate())
        );
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() == null) {
            return null;
        }
        String stripped = NewsTextUtils.stripHtmlTags(entry.getDescription().getValue());
        return NewsTextUtils.decodeHtml(stripped);
    }

    private String extractContent(SyndEntry entry) {
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            for (SyndContent content : contents) {
                if (content.getValue() != null && !content.getValue().isBlank()) {
                    return content.getValue().trim();
                }
            }
        }

        for (Element element : entry.getForeignMarkup()) {
            if ("encoded".equals(element.getName())) {
                String value = element.getValue();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }

        return null;
    }

    private String extractGuid(SyndEntry entry) {
        if (entry.getUri() != null && !entry.getUri().isBlank()) {
            return entry.getUri().trim();
        }
        return null;
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
