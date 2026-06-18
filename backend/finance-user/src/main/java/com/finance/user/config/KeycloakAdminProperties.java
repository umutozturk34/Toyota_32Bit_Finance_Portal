package com.finance.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound {@code keycloak.admin.*} configuration: realm, admin credentials, token endpoint, and refresh safety margin for the admin client. */
@Getter
@Setter
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    private String baseUrl = "http://keycloak:8080";
    private String realm = "finance-realm";
    private String adminUser = "admin";
    private String adminPassword = "";
    private long tokenSafetyMarginSeconds = 10;
    private String tokenClientId = "admin-cli";
    private String tokenPath = "/realms/master/protocol/openid-connect/token";
    private String otpCredentialType = "otp";
    // Bounded HTTP timeouts: a hung Keycloak must not pin Tomcat workers on the blocking admin calls.
    private long responseTimeoutSeconds = 10;
    private int connectTimeoutMillis = 5000;
}
