package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.service.TotpService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/totp")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://localhost", "http://localhost:80"})
public class TotpController {

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTotpStatus(Authentication authentication) {
        String username = authentication.getName();
        boolean configured = totpService.isTotpConfigured(username);
        
        Map<String, Object> data = new HashMap<>();
        data.put("configured", configured);
        data.put("username", username);
        
        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "TOTP status retrieved successfully",
            data,
            LocalDateTime.now()
        ));
    }

    @GetMapping("/setup-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSetupUrl() {
        Map<String, String> data = new HashMap<>();
        data.put("url", totpService.getAccountManagementUrl());
        
        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Setup URL retrieved successfully",
            data,
            LocalDateTime.now()
        ));
    }
}
