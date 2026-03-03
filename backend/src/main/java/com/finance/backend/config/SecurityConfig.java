    package com.finance.backend.config;
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
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
    import java.util.Collection;
    import java.util.List;
    import java.util.Map;
    import java.util.stream.Collectors;
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    public class SecurityConfig {
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
        private String jwkSetUri;
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/auth/**", "/login", "/register").permitAll()
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
                        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"JWT token required\"}");
                    })
                )
                .exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"No JWT token provided\"}");
                    })
                )
                .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
            return http.build();
        }
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(List.of(
                "http://localhost",
                "http://localhost:*",
                "http://127.0.0.1",
                "http://127.0.0.1:*"
            ));
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control", "X-Requested-With"));
            configuration.setAllowCredentials(true);
            configuration.setExposedHeaders(List.of("Authorization"));
            configuration.setMaxAge(3600L);
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
