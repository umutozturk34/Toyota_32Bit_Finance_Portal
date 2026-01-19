package com.finance.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TotpService {

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getAdminToken() {
        String tokenUrl = keycloakServerUrl + "/realms/master/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, String> params = new HashMap<>();
        params.put("username", adminUsername);
        params.put("password", adminPassword);
        params.put("grant_type", "password");
        params.put("client_id", "admin-cli");

        String body = "username=" + adminUsername + 
                     "&password=" + adminPassword + 
                     "&grant_type=password&client_id=admin-cli";

        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return (String) response.getBody().get("access_token");
    }

    public boolean isTotpConfigured(String username) {
        try {
            String token = getAdminToken();
            String userUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users?username=" + username;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(userUrl, HttpMethod.GET, request, List.class);
            List<Map<String, Object>> users = response.getBody();

            if (users != null && !users.isEmpty()) {
                String userId = (String) users.get(0).get("id");
                
                String credentialsUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users/" + userId + "/credentials";
                ResponseEntity<List> credResponse = restTemplate.exchange(credentialsUrl, HttpMethod.GET, request, List.class);
                List<Map<String, Object>> credentials = credResponse.getBody();

                if (credentials != null) {
                    return credentials.stream()
                        .anyMatch(cred -> "otp".equals(cred.get("type")));
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String getAccountManagementUrl() {
        return "http://localhost:8180/realms/" + realm + "/account/#/security/signingin";
    }
}
