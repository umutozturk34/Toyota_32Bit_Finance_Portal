package com.finance.market.fund.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FundTest {

    private Fund fund() {
        Fund f = new Fund();
        f.setFundCode("TI2");
        f.setPrice(new BigDecimal("1.234567890"));
        f.setBulletinPrice(new BigDecimal("1.500000"));
        f.setShareCount(new BigDecimal("100.123"));
        f.setInvestorCount(new BigDecimal("5000.999"));
        f.setPortfolioSize(new BigDecimal("123456.789"));
        return f;
    }

    @Test
    void getCode_returnsFundCode() {
        assertThat(fund().getCode()).isEqualTo("TI2");
    }

    @Test
    void getPriceTry_returnsPrice() {
        assertThat(fund().getPriceTry()).isEqualByComparingTo("1.234567890");
    }

    @Test
    void scaleFields_appliesProjectScale_toEachMonetaryField() {
        Fund f = fund();

        f.scaleFields(4);

        assertThat(f.getPrice().scale()).isEqualTo(6);
        assertThat(f.getBulletinPrice().scale()).isEqualTo(4);
        assertThat(f.getShareCount().scale()).isEqualTo(2);
        assertThat(f.getInvestorCount().scale()).isEqualTo(2);
        assertThat(f.getPortfolioSize().scale()).isEqualTo(2);
    }

    @Test
    void applyScaling_keepsBulletinDropsInvestor_whenFundTypeIsByf() {
        Fund f = fund();

        f.applyScaling(FundType.BYF);

        assertThat(f.getBulletinPrice()).isNotNull();
        assertThat(f.getInvestorCount()).isNull();
    }

    @Test
    void applyScaling_dropsBulletinKeepsInvestor_whenFundTypeIsYat() {
        Fund f = fund();

        f.applyScaling(FundType.YAT);

        assertThat(f.getBulletinPrice()).isNull();
        assertThat(f.getInvestorCount()).isNotNull();
    }

    @Test
    void applyScaling_nullableFundType_dropsBulletinAndInvestor() {
        Fund f = fund();

        f.applyScaling(null);

        assertThat(f.getBulletinPrice()).isNull();
        assertThat(f.getInvestorCount()).isNull();
    }
}
