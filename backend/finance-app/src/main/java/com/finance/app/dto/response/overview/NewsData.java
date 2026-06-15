package com.finance.app.dto.response.overview;

import com.finance.news.dto.response.NewsAssetResponse;

import java.time.OffsetDateTime;
import java.util.List;

/** NEWS widget payload: the categories that fed the list and the resulting article rows. */
public record NewsData(
        List<String> categoriesUsed,
        List<NewsRow> items
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.NEWS;
    }

    /** One news article: its id, title, category, optional image, source name, publish timestamp, and the assets it mentions. */
    public record NewsRow(
            Long id,
            String title,
            String category,
            String imageUrl,
            String sourceName,
            OffsetDateTime publishedAt,
            List<NewsAssetResponse> assets
    ) {
    }
}
