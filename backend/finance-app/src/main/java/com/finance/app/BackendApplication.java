package com.finance.app;

import com.finance.bond.config.BondProperties;
import com.finance.common.config.AppProperties;
import com.finance.common.config.CommissionProperties;
import com.finance.commodity.config.CommodityProperties;
import com.finance.crypto.config.CryptoProperties;
import com.finance.forex.config.ForexProperties;
import com.finance.fund.config.FundProperties;
import com.finance.news.config.NewsProperties;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.stock.config.StockProperties;
import com.finance.user.config.KeycloakAdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.finance")
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({
        AppProperties.class,
        BondProperties.class,
        CommissionProperties.class,
        CommodityProperties.class,
        CryptoProperties.class,
        ForexProperties.class,
        FundProperties.class,
        KeycloakAdminProperties.class,
        NewsProperties.class,
        PortfolioProperties.class,
        StockProperties.class
})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
