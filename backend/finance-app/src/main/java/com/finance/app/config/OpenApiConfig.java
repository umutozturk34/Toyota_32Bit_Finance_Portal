package com.finance.app.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${KEYCLOAK_EXTERNAL_URL:http://localhost:8180}")
    private String keycloakUrl;

    @Bean
    public OpenAPI openAPI() {
        String authUrl = keycloakUrl + "/realms/finance-realm/protocol/openid-connect/auth";
        String tokenUrl = keycloakUrl + "/realms/finance-realm/protocol/openid-connect/token";

        return new OpenAPI()
                .info(new Info()
                        .title("Finance Portal API")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("keycloak"))
                .components(new Components()
                        .addSecuritySchemes("keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl)
                                                .tokenUrl(tokenUrl)))));
    }
}
