package com.finance.app.dto.response.overview;

import java.time.OffsetDateTime;
import java.util.List;

public record NewsData(
        List<String> categoriesUsed,
        List<NewsRow> items
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.NEWS;
    }

    public record NewsRow(
            Long id,
            String title,
            String category,
            String imageUrl,
            String sourceName,
            OffsetDateTime publishedAt
    ) {
    }
}
