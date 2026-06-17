package com.finance;

import com.finance.app.config.AnalyticsProperties;
import com.finance.app.config.OverviewProperties;
import com.finance.market.bond.config.BondProperties;
import com.finance.common.config.AppProperties;
import com.finance.common.event.KafkaAdminProperties;
import com.finance.common.event.KafkaTopicsProperties;
import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.config.FxProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.macro.config.MacroProperties;
import com.finance.news.config.NewsClassifierProperties;
import com.finance.news.config.NewsProperties;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.market.stock.config.StockProperties;
import com.finance.market.viop.config.ViopProperties;
import com.finance.market.viop.config.ViopUnderlyingRules;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.config.KeycloakAdminProperties;
import com.finance.user.config.UserSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({
        AnalyticsProperties.class,
        AppProperties.class,
        BondProperties.class,
        ChartDefaultsProperties.class,
        CommodityProperties.class,
        CryptoProperties.class,
        ForexProperties.class,
        FxProperties.class,
        FundProperties.class,
        KafkaAdminProperties.class,
        KafkaTopicsProperties.class,
        KeycloakAdminProperties.class,
        MacroProperties.class,
        NewsClassifierProperties.class,
        NewsProperties.class,
        OverviewProperties.class,
        PortfolioProperties.class,
        StockProperties.class,
        UserSecurityProperties.class,
        ViopProperties.class,
        ViopUnderlyingRules.class
})
public class BackendApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        SpringApplication.run(BackendApplication.class, args);
    }
}
