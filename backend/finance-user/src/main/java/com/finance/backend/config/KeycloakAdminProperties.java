package com.finance.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    private String baseUrl = "http://keycloak:8080";
    private String realm = "finance-realm";
    private String adminUser = "admin";
    private String adminPassword = "";
    private long tokenSafetyMarginSeconds = 10;
}
