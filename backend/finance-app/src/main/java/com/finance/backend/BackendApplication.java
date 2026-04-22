package com.finance.backend;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.BondProperties;
import com.finance.backend.config.CommodityProperties;
import com.finance.backend.config.CryptoProperties;
import com.finance.backend.config.ForexProperties;
import com.finance.backend.config.FundProperties;
import com.finance.backend.config.NewsProperties;
import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.config.StockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({AppProperties.class, BondProperties.class, CommodityProperties.class, CryptoProperties.class, ForexProperties.class, FundProperties.class, NewsProperties.class, PortfolioProperties.class, StockProperties.class})
public class BackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}
}
