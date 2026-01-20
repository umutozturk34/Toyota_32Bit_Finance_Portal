package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.entity.MetalPrice;
import com.finance.backend.service.MetalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metals")
@RequiredArgsConstructor
public class MetalController {
    
    private final MetalService metalService;
    
    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MetalPrice>>> getLatestPrices() {
        List<MetalPrice> metals = metalService.getLatestPrices();
        return ResponseEntity.ok(ApiResponse.success("Latest precious metal prices retrieved successfully", metals));
    }
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<MetalPrice>>> getAllPrices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<MetalPrice> metals = metalService.getLatestPrices(pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Precious metal prices retrieved successfully", metals));
    }
    
    @GetMapping("/{symbol}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MetalPrice>>> getMetalHistory(@PathVariable String symbol) {
        List<MetalPrice> history = metalService.getMetalHistory(symbol.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success("Metal price history retrieved successfully", history));
    }
}