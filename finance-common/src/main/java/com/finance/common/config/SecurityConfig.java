    package com.finance.common.config;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.finance.common.filter.RateLimitFilter;
    import com.finance.common.filter.RateLimitTier;
    import io.lettuce.core.RedisClient;
    import io.lettuce.core.api.StatefulRedisConnection;
    import io.lettuce.core.codec.ByteArrayCodec;
    import io.lettuce.core.codec.RedisCodec;
    import io.lettuce.core.codec.StringCodec;
    import lombok.RequiredArgsConstructor;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.HttpMethod;
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
    import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
    import org.springframework.security.config.http.SessionCreationPolicy;
    import org.springframework.security.core.authority.SimpleGrantedAuthority;
    import org.springframework.security.oauth2.jwt.JwtDecoder;
    import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
    import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
    import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
    import org.springframework.data.redis.connection.RedisConnectionFactory;
    import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
    import java.util.Collection;
    import java.util.List;
    import java.util.Map;
    import java.util.stream.Collectors;
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    @RequiredArgsConstructor
    public class SecurityConfig {
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
        private String jwkSetUri;
        private final AppProperties appProperties;
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter, com.finance.common.i18n.Translator translator) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/auth/**", "/login", "/register").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        String msg = translator.translate("error.auth.tokenRequired").replace("\"", "\\\"");
                        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + msg + "\"}");
                    })
                )
                .exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        String msg = translator.translate("error.auth.tokenMissing").replace("\"", "\\\"");
                        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + msg + "\"}");
                    })
                )
                .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
            http.addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class);
            return http.build();
        }
        @Bean
        public RateLimitFilter rateLimitFilterFactory(ObjectMapper objectMapper, AppProperties appProperties, RedisConnectionFactory redisConnectionFactory, List<RateLimitTier> tiers, com.finance.common.i18n.Translator translator) {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
            RedisClient redisClient = (RedisClient) lettuceFactory.getNativeClient();
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                    RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            );
            return new RateLimitFilter(objectMapper, appProperties, connection, tiers, translator);
        }
        
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            AppProperties.Cors cors = appProperties.getSecurity().getCors();
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(cors.getAllowedOriginPatterns());
            configuration.setAllowedMethods(cors.getAllowedMethods());
            configuration.setAllowedHeaders(cors.getAllowedHeaders());
            configuration.setAllowCredentials(cors.isAllowCredentials());
            configuration.setExposedHeaders(cors.getExposedHeaders());
            configuration.setMaxAge(cors.getMaxAgeSeconds());
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }
        @Bean
        public JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                if (realmAccess == null) {
                    return List.of();
                }
                @SuppressWarnings("unchecked")
                Collection<String> roles = (Collection<String>) realmAccess.get("roles");
                if (roles == null) {
                    return List.of();
                }
                return roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());
            });
            return converter;
        }
    }
