package com.finance.news.mapper;

import com.finance.common.config.AppProperties;
import com.finance.news.config.NewsProperties;
import com.finance.news.dto.internal.RssArticleData;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssClientMapperTest {

    private RssClientMapper mapper;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        NewsProperties newsProperties = new NewsProperties();
        mapper = new RssClientMapper(appProperties, newsProperties);
    }

    private SyndEntry entry(String title, String link, String description) {
        SyndEntry entry = new SyndEntryImpl();
        entry.setTitle(title);
        entry.setLink(link);
        if (description != null) {
            SyndContent desc = new SyndContentImpl();
            desc.setValue(description);
            entry.setDescription(desc);
        }
        entry.setPublishedDate(new Date(1715472000000L));
        return entry;
    }

    private SyndFeed feed(SyndEntry... entries) {
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setEntries(java.util.Arrays.asList(entries));
        return feed;
    }

    @Test
    void toArticleDataList_filtersEntriesMissingTitleOrLink() {
        SyndEntry valid = entry("Title", "http://example.com/article", "Body");
        SyndEntry missingLink = entry("Title2", null, "Body2");
        SyndEntry missingTitle = entry(null, "http://example.com/x", "Body3");

        List<RssArticleData> result = mapper.toArticleDataList(feed(valid, missingLink, missingTitle));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Title");
    }

    @Test
    void toArticleDataList_extractsPlainTextFromHtmlDescription() {
        SyndEntry e = entry("News", "http://example.com",
                "<p>Hello <strong>world</strong></p>");

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).description()).isEqualTo("Hello world");
    }

    @Test
    void toArticleDataList_promotesRichHtmlDescription_toContent_whenNoContentEncoded() {
        String richHtml = "<p>" + "lorem ipsum ".repeat(40) + "</p>";
        SyndEntry e = entry("News", "http://example.com", richHtml);

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).content()).isEqualTo(richHtml);
    }

    @Test
    void toArticleDataList_usesContentsCollection_whenAvailable() {
        SyndEntry e = entry("News", "http://example.com", "short desc");
        SyndContent body = new SyndContentImpl();
        body.setValue("<article>Full body</article>");
        e.setContents(List.of(body));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).content()).isEqualTo("<article>Full body</article>");
    }

    @Test
    void toArticleDataList_extractsImageFromEnclosure_whenImageTypePresent() {
        SyndEntry e = entry("News", "http://example.com", "body");
        SyndEnclosure enc = new SyndEnclosureImpl();
        enc.setUrl("https://example.com/img.jpg");
        enc.setType("image/jpeg");
        e.setEnclosures(List.of(enc));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/img.jpg");
    }

    @Test
    void toArticleDataList_skipsEnclosure_whenTypeIsNonImage() {
        SyndEntry e = entry("News", "http://example.com", "body");
        SyndEnclosure enc = new SyndEnclosureImpl();
        enc.setUrl("https://example.com/audio.mp3");
        enc.setType("audio/mpeg");
        e.setEnclosures(List.of(enc));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).imageUrl()).isNull();
    }

    @Test
    void toArticleDataList_extractsImageFromMediaThumbnail_foreignMarkup() {
        SyndEntry e = entry("News", "http://example.com", "body");
        Namespace media = Namespace.getNamespace("media", "http://search.yahoo.com/mrss/");
        Element thumb = new Element("thumbnail", media);
        thumb.setAttribute("url", "https://example.com/thumb.png");
        e.setForeignMarkup(List.of(thumb));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/thumb.png");
    }

    @Test
    void toArticleDataList_extractsImageFromImageElement_byUrlAttribute() {
        SyndEntry e = entry("News", "http://example.com", "body");
        Element img = new Element("image");
        img.setAttribute("url", "https://example.com/i.png");
        e.setForeignMarkup(List.of(img));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/i.png");
    }

    @Test
    void toArticleDataList_extractsImageFromImageElement_byHttpText() {
        SyndEntry e = entry("News", "http://example.com", "body");
        Element img = new Element("image");
        img.setText("https://example.com/text.png");
        e.setForeignMarkup(List.of(img));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/text.png");
    }

    @Test
    void toArticleDataList_picksContentEncoded_overEmptyContents() {
        SyndEntry e = entry("News", "http://example.com", "body");
        Element encoded = new Element("encoded");
        encoded.setText("<p>encoded body</p>");
        e.setForeignMarkup(List.of(encoded));

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).content()).isEqualTo("<p>encoded body</p>");
    }

    @Test
    void toArticleDataList_setsGuid_fromEntryUri() {
        SyndEntry e = entry("News", "http://example.com", "body");
        e.setUri("urn:news:42");

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).guid()).isEqualTo("urn:news:42");
    }

    @Test
    void toArticleDataList_usesNowAsPublishedAt_whenEntryDateMissing() {
        SyndEntry e = entry("News", "http://example.com", "body");
        e.setPublishedDate(null);

        List<RssArticleData> result = mapper.toArticleDataList(feed(e));

        assertThat(result.get(0).publishedAt()).isNotNull();
    }
}
