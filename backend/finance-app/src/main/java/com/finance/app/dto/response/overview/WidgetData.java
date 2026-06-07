package com.finance.app.dto.response.overview;

/** Sealed base for a rendered widget's payload; the permitted subtypes are the supported widget kinds. */
public sealed interface WidgetData
        permits AssetCardsData, MoverData, WatchlistData, NewsData, BenchmarkBeatersData, AssetReturnsData {

    WidgetKind kind();
}
