package com.finance.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Application-wide configuration
 * Provides RestTemplate for external API calls
 */
@Configuration
public class AppConfig {

    /**
     * Configure RestTemplate with timeout settings
     * Used for CoinGecko API calls
     * 
     * @return Configured RestTemplate with 5000ms timeout
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        
        return new RestTemplate(factory);
    }
}
