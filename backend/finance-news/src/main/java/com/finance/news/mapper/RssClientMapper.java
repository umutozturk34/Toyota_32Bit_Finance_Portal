package com.finance.news.mapper;

import com.finance.common.config.AppProperties;
import com.finance.news.config.NewsProperties;
import com.finance.news.dto.internal.RssArticleData;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.finance.news.util.NewsTextUtils;
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

    private final ZoneId zone;
    private final int richHtmlMinLength;

    public RssClientMapper(AppProperties appProperties, NewsProperties newsProperties) {
        this.zone = ZoneId.of(appProperties.getTimezone());
        this.richHtmlMinLength = newsProperties.getMapping().getRichHtmlMinLength();
    }

    public List<RssArticleData> toArticleDataList(SyndFeed feed) {
        return feed.getEntries().stream()
                .filter(entry -> entry.getTitle() != null && entry.getLink() != null)
                .map(this::toArticleData)
                .toList();
    }

    private RssArticleData toArticleData(SyndEntry entry) {
        String rawDescriptionHtml = extractRawDescription(entry);
        String plainDescription = toPlainText(rawDescriptionHtml);
        String content = extractContent(entry);

        if (content == null && isRichHtml(rawDescriptionHtml)) {
            content = rawDescriptionHtml;
        }

        String imageUrl = extractImageUrl(entry);
        if (imageUrl == null && content != null) {
            imageUrl = NewsTextUtils.extractFirstImageUrl(content);
        }
        if (imageUrl == null && rawDescriptionHtml != null) {
            imageUrl = NewsTextUtils.extractFirstImageUrl(rawDescriptionHtml);
        }

        String cleanedContent = NewsTextUtils.stripCoverImageFromContent(content, imageUrl);

        return new RssArticleData(
                NewsTextUtils.decodeHtml(entry.getTitle().trim()),
                entry.getLink().trim(),
                plainDescription,
                cleanedContent,
                imageUrl,
                extractGuid(entry),
                convertDate(entry.getPublishedDate())
        );
    }

    private String extractRawDescription(SyndEntry entry) {
        if (entry.getDescription() == null) {
            return null;
        }
        String value = entry.getDescription().getValue();
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String toPlainText(String html) {
        if (html == null) {
            return null;
        }
        String stripped = NewsTextUtils.stripHtmlTags(html);
        return NewsTextUtils.decodeHtml(stripped);
    }

    private boolean isRichHtml(String text) {
        return text != null && text.contains("<") && text.length() > richHtmlMinLength;
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
            SyndEnclosure first = enclosures.getFirst();
            String url = first.getUrl();
            if (url != null && !url.isBlank()) {
                String type = first.getType();
                if (type == null || type.startsWith("image")) {
                    return url.trim();
                }
            }
        }

        for (Element element : entry.getForeignMarkup()) {
            String name = element.getName();

            if ("thumbnail".equals(name) || "content".equals(name)) {
                String url = element.getAttributeValue("url");
                if (url != null) {
                    return url;
                }
            }

            if ("image".equals(name)) {
                String url = element.getAttributeValue("url");
                if (url != null) {
                    return url;
                }
                String textUrl = element.getTextTrim();
                if (textUrl != null && textUrl.startsWith("http")) {
                    return textUrl;
                }
            }
        }

        return null;
    }

    private LocalDateTime convertDate(Date date) {
        if (date == null) {
            return LocalDateTime.now(zone);
        }
        return date.toInstant().atZone(zone).toLocalDateTime();
    }
}
