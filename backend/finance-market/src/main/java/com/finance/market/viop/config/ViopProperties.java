package com.finance.market.viop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.market.viop")
public record ViopProperties(
        String baseUrl,
        String contractListPagePath,
        String viopAnalysisPagePath,
        String vadeliMetadataPath,
        String opsiyonMetadataPath,
        String oneEndeksPath,
        String chartDataPath,
        String userAgent,
        Duration sessionTtl,
        Duration requestTimeout
) {

    public ViopProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.isyatirim.com.tr";
        }
        if (contractListPagePath == null || contractListPagePath.isBlank()) {
            contractListPagePath = "/tr-tr/urunler/Sayfalar/Islem-Goren-Kontratlar.aspx";
        }
        if (viopAnalysisPagePath == null || viopAnalysisPagePath.isBlank()) {
            viopAnalysisPagePath = "/tr-tr/analiz/Sayfalar/viop.aspx";
        }
        if (vadeliMetadataPath == null || vadeliMetadataPath.isBlank()) {
            vadeliMetadataPath = "/_layouts/15/Isyatirim.Website/Common/Data.aspx/VadeliIslemler";
        }
        if (opsiyonMetadataPath == null || opsiyonMetadataPath.isBlank()) {
            opsiyonMetadataPath = "/_layouts/15/Isyatirim.Website/Common/Data.aspx/OpsiyonSozlesmeleri";
        }
        if (oneEndeksPath == null || oneEndeksPath.isBlank()) {
            oneEndeksPath = "/_layouts/15/Isyatirim.Website/Common/Data.aspx/OneEndeks";
        }
        if (chartDataPath == null || chartDataPath.isBlank()) {
            chartDataPath = "/_Layouts/15/IsYatirim.Website/Common/ChartData.aspx/IndexHistoricalAll";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/26.2 Safari/605.1.15";
        }
        if (sessionTtl == null) sessionTtl = Duration.ofMinutes(30);
        if (requestTimeout == null) requestTimeout = Duration.ofSeconds(20);
    }
}
