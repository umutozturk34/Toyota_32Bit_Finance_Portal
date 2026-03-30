package com.finance.backend.util;

import org.springframework.web.util.HtmlUtils;

public final class NewsTextUtils {

    private NewsTextUtils() {
    }

    public static String decodeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return HtmlUtils.htmlUnescape(text);
    }

    public static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
