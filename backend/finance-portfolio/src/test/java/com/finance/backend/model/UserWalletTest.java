package com.finance.backend.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UserWalletTest {

    @Test
    void debitReducesBothBalanceAndAvailableBalance() {
        UserWallet wallet = buildWallet(new BigDecimal("1000.0000"));

        wallet.debit(new BigDecimal("300.0000"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("700.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("700.0000"));
    }

    @Test
    void creditIncreasesBothBalanceAndAvailableBalance() {
        UserWallet wallet = buildWallet(new BigDecimal("500.0000"));

        wallet.credit(new BigDecimal("250.0000"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("750.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("750.0000"));
    }

    @Test
    void debitMaintainsScale4() {
        UserWallet wallet = buildWallet(new BigDecimal("1000.0000"));

        wallet.debit(new BigDecimal("333.3333"));

        assertThat(wallet.getBalance().scale()).isEqualTo(4);
        assertThat(wallet.getAvailableBalance().scale()).isEqualTo(4);
    }

    @Test
    void hasSufficientBalanceReturnsTrueWhenEnough() {
        UserWallet wallet = buildWallet(new BigDecimal("1000.0000"));

        assertThat(wallet.hasSufficientBalance(new BigDecimal("999.9999"))).isTrue();
        assertThat(wallet.hasSufficientBalance(new BigDecimal("1000.0000"))).isTrue();
    }

    @Test
    void hasSufficientBalanceReturnsFalseWhenInsufficient() {
        UserWallet wallet = buildWallet(new BigDecimal("100.0000"));

        assertThat(wallet.hasSufficientBalance(new BigDecimal("100.0001"))).isFalse();
    }

    @Test
    void debitThenCreditRestoresOriginalBalance() {
        UserWallet wallet = buildWallet(new BigDecimal("500.0000"));

        wallet.debit(new BigDecimal("200.0000"));
        wallet.credit(new BigDecimal("200.0000"));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("500.0000"));
    }

    private UserWallet buildWallet(BigDecimal balance) {
        return UserWallet.builder()
                .balance(balance)
                .availableBalance(balance)
                .build();
    }
}
