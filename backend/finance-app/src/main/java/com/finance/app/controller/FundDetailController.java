package com.finance.app.controller;

import com.finance.market.fund.repository.FundRepository;
import com.finance.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets/funds")
@RequiredArgsConstructor
public class FundDetailController {

    private final FundRepository fundRepository;

    @GetMapping("/sub-categories")
    public ApiResponse<List<String>> getSubCategories() {
        return ApiResponse.success(fundRepository.findDistinctSubCategories());
    }
}
