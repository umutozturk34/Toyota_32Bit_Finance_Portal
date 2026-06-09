package com.finance.app.controller;

import com.finance.market.fund.repository.FundRepository;
import com.finance.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read API for fund-specific reference data (e.g. the distinct fund sub-categories used as filters). */
@RestController
@RequestMapping("/api/v1/markets/funds")
@RequiredArgsConstructor
@Validated
public class FundDetailController {

    private final FundRepository fundRepository;

    @GetMapping("/sub-categories")
    public ApiResponse<List<String>> getSubCategories() {
        return ApiResponse.success(fundRepository.findDistinctSubCategories());
    }
}
