package com.finance.market.fund.mapper;

import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundAllocation;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundAllocationRepository;
import com.finance.shared.dto.response.FundMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundResponseMapperTest {

    @Mock private FundAllocationRepository allocationRepository;

    private FundResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FundResponseMapperImpl();
        ReflectionTestUtils.setField(mapper, "allocationRepository", allocationRepository);
    }

    private Fund baseFund(String code) {
        Fund fund = Fund.builder().build();
        fund.setFundCode(code);
        fund.setFundType(FundType.YAT);
        fund.setPortfolioSize(new BigDecimal("1000.00"));
        fund.setInvestorCount(new BigDecimal("500.00"));
        fund.setBulletinPrice(new BigDecimal("12.3456"));
        fund.setShareCount(new BigDecimal("250.00"));
        fund.setRiskValue(4);
        fund.setCategory("Equity");
        fund.setSubCategory("Hisse Senedi");
        fund.setCategoryRank(3);
        fund.setCategoryTotalFunds(50);
        fund.setMarketShare(new BigDecimal("0.1234"));
        fund.setReturn1m(new BigDecimal("1.5"));
        fund.setReturn3m(new BigDecimal("4.2"));
        fund.setReturn6m(new BigDecimal("8.7"));
        fund.setReturn1y(new BigDecimal("12.0"));
        fund.setReturnYtd(new BigDecimal("9.0"));
        fund.setReturn3y(new BigDecimal("30.0"));
        fund.setReturn5y(new BigDecimal("60.0"));
        return fund;
    }

    private FundAllocation allocation(String fundCode, String assetClass, String percentage) {
        return FundAllocation.builder()
                .fundCode(fundCode)
                .assetClass(assetClass)
                .percentage(new BigDecimal(percentage))
                .build();
    }

    @Test
    void should_embedAllocationEntries_when_repositoryReturnsRows() {
        Fund fund = baseFund("AAA");
        when(allocationRepository.findByFundCodeOrderByPercentageDesc("AAA"))
                .thenReturn(List.of(
                        allocation("AAA", "EQUITY", "60.0000"),
                        allocation("AAA", "BOND", "40.0000")));

        FundMetadata metadata = mapper.buildMetadata(fund);

        assertThat(metadata.fundType()).isEqualTo("YAT");
        assertThat(metadata.subCategory()).isEqualTo("Hisse Senedi");
        assertThat(metadata.allocations()).hasSize(2);
        assertThat(metadata.allocations().get(0).assetClass()).isEqualTo("EQUITY");
        assertThat(metadata.allocations().get(0).percentage()).isEqualByComparingTo("60.0000");
        assertThat(metadata.allocations().get(1).assetClass()).isEqualTo("BOND");
        assertThat(metadata.allocations().get(1).percentage()).isEqualByComparingTo("40.0000");
    }

    @Test
    void should_returnEmptyAllocationsList_when_repositoryReturnsNoRows() {
        Fund fund = baseFund("BBB");
        when(allocationRepository.findByFundCodeOrderByPercentageDesc("BBB"))
                .thenReturn(List.of());

        FundMetadata metadata = mapper.buildMetadata(fund);

        assertThat(metadata.allocations()).isEmpty();
        assertThat(metadata.portfolioSize()).isEqualByComparingTo("1000.00");
        assertThat(metadata.riskValue()).isEqualTo(4);
    }

    @Test
    void should_mapNullFundType_when_fundHasNoType() {
        Fund fund = baseFund("CCC");
        fund.setFundType(null);
        when(allocationRepository.findByFundCodeOrderByPercentageDesc("CCC"))
                .thenReturn(List.of());

        FundMetadata metadata = mapper.buildMetadata(fund);

        assertThat(metadata.fundType()).isNull();
        assertThat(metadata.allocations()).isEmpty();
    }
}
