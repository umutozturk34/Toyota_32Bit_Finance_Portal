package com.finance.news.util;


import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML cleanup helpers for feed content: unescape entities, strip tags, extract the first inline image
 * URL, and remove a duplicate cover image (and any resulting empty leading blocks) from article bodies.
 */
public final class NewsTextUtils {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile(
            "<img[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIGURE_PATTERN = Pattern.compile(
            "<figure[^>]*>.*?</figure>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LEADING_EMPTY_BLOCK_PATTERN = Pattern.compile(
            "^\\s*(<p>\\s*</p>|<div>\\s*</div>|<br\\s*/?>)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESIZE_SUFFIX_PATTERN = Pattern.compile(
            "[-_]\\d{2,4}x\\d{2,4}$");

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
        return HTML_TAG_PATTERN.matcher(html).replaceAll("").trim();
    }

    public static String extractFirstImageUrl(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = IMG_SRC_PATTERN.matcher(html);
        if (matcher.find()) {
            String url = matcher.group(1).trim();
            return url.isEmpty() ? null : url;
        }
        return null;
    }

    /** Removes figures/img tags matching the cover image from the body so it isn't shown twice, then trims empty leading blocks. */
    public static String stripCoverImageFromContent(String content, String coverImageUrl) {
        if (content == null || content.isEmpty() || coverImageUrl == null || coverImageUrl.isEmpty()) {
            return content;
        }

        String coverSlug = normalizeImageUrl(coverImageUrl);
        String result = content;

        result = replaceMatchingFigures(result, coverSlug);
        result = replaceMatchingImages(result, coverSlug);

        result = LEADING_EMPTY_BLOCK_PATTERN.matcher(result).replaceAll("");
        return result.trim();
    }

    private static String replaceMatchingFigures(String html, String coverSlug) {
        StringBuilder sb = new StringBuilder();
        Matcher m = FIGURE_PATTERN.matcher(html);
        while (m.find()) {
            String figureImgUrl = extractImgSrcFrom(m.group());
            if (figureImgUrl != null && isSameImage(normalizeImageUrl(figureImgUrl), coverSlug)) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceMatchingImages(String html, String coverSlug) {
        StringBuilder sb = new StringBuilder();
        Matcher m = IMG_TAG_PATTERN.matcher(html);
        while (m.find()) {
            String imgSrc = extractImgSrcFrom(m.group());
            if (imgSrc != null && isSameImage(normalizeImageUrl(imgSrc), coverSlug)) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractImgSrcFrom(String html) {
        Matcher m = IMG_SRC_PATTERN.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Fuzzy image-identity check tolerating CDN/resize variants by comparing URLs, containment, and filename stems. */
    private static boolean isSameImage(String a, String b) {
        if (a.equals(b)) return true;
        if (a.contains(b) || b.contains(a)) return true;
        String stemA = extractStem(a);
        String stemB = extractStem(b);
        return stemA != null && stemB != null && stemA.length() > 5
                && (stemA.equals(stemB) || stemA.startsWith(stemB) || stemB.startsWith(stemA));
    }

    private static String normalizeImageUrl(String url) {
        int query = url.indexOf('?');
        return query > 0 ? url.substring(0, query) : url;
    }

    /** Reduces a URL to its filename without extension or trailing {@code -WxH} resize suffix for comparison. */
    private static String extractStem(String url) {
        int slash = url.lastIndexOf('/');
        String filename = slash >= 0 && slash < url.length() - 1 ? url.substring(slash + 1) : url;
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return filename;
        String name = filename.substring(0, dot);
        return RESIZE_SUFFIX_PATTERN.matcher(name).replaceAll("");
    }
}
