package com.finance.backend.model;

import com.finance.backend.model.value.MoneyTRY;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserWalletTest {

    @Test
    void debitReducesBothBalanceAndAvailableBalance() {
        UserWallet wallet = buildWallet(MoneyTRY.of("1000.0000"));

        wallet.debit(MoneyTRY.of("300.0000"));

        assertThat(wallet.getBalance()).isEqualTo(MoneyTRY.of("700.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualTo(MoneyTRY.of("700.0000"));
    }

    @Test
    void creditIncreasesBothBalanceAndAvailableBalance() {
        UserWallet wallet = buildWallet(MoneyTRY.of("500.0000"));

        wallet.credit(MoneyTRY.of("250.0000"));

        assertThat(wallet.getBalance()).isEqualTo(MoneyTRY.of("750.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualTo(MoneyTRY.of("750.0000"));
    }

    @Test
    void debitMaintainsScaleFour() {
        UserWallet wallet = buildWallet(MoneyTRY.of("1000.0000"));

        wallet.debit(MoneyTRY.of("333.3333"));

        assertThat(wallet.getBalance().amount().scale()).isEqualTo(4);
        assertThat(wallet.getAvailableBalance().amount().scale()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({
            "1000.0000, 999.9999, true",
            "1000.0000, 1000.0000, true",
            "100.0000, 100.0001, false",
            "0.0000, 0.0001, false",
            "500.0000, 500.0000, true"
    })
    void hasSufficientBalanceComparesAgainstAvailableBalance(String startingBalance, String probe, boolean expected) {
        UserWallet wallet = buildWallet(MoneyTRY.of(startingBalance));

        boolean sufficient = wallet.hasSufficientBalance(MoneyTRY.of(probe));

        assertThat(sufficient).isEqualTo(expected);
    }

    @Test
    void debitThenCreditRestoresOriginalBalance() {
        UserWallet wallet = buildWallet(MoneyTRY.of("500.0000"));

        wallet.debit(MoneyTRY.of("200.0000"));
        wallet.credit(MoneyTRY.of("200.0000"));

        assertThat(wallet.getBalance()).isEqualTo(MoneyTRY.of("500.0000"));
        assertThat(wallet.getAvailableBalance()).isEqualTo(MoneyTRY.of("500.0000"));
    }

    private UserWallet buildWallet(MoneyTRY balance) {
        return UserWallet.builder()
                .balance(balance)
                .availableBalance(balance)
                .build();
    }
}
