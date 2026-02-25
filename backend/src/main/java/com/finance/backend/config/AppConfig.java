package com.finance.backend.config;
import com.finance.backend.client.ThrottlingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
@Configuration
public class AppConfig {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return buildRestTemplate();
    }
    @Bean("yahooRestTemplate")
    public RestTemplate yahooRestTemplate() {
        RestTemplate rt = buildRestTemplate();
        rt.getInterceptors().add(new ThrottlingInterceptor(2000));
        return rt;
    }
    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
        restTemplate.getInterceptors().add((request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            if (headers.getFirst(HttpHeaders.USER_AGENT) == null) {
                headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            }
            return execution.execute(request, body);
        });
        return restTemplate;
    }
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
