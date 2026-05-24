package com.finance.app.dto.response.overview;

public sealed interface WidgetData
        permits AssetCardsData, MoverData, WatchlistData, NewsData, BenchmarkBeatersData, SingleAssetData {

    WidgetKind kind();
}
