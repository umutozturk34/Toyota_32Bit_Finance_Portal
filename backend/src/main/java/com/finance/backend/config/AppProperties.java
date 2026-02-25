package com.finance.backend.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Api api = new Api();
    private String tcmbXmlUrl;
    @Getter
    @Setter
    public static class Api {
        private Provider coingecko = new Provider();
        private Provider yahoo = new Provider();
    }
    @Getter
    @Setter
    public static class Provider {
        private String baseUrl;
    }
}
