package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundBulkFetchExecutorTest {

    @Mock
    private TefasClient tefasClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private FundBulkFetchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FundBulkFetchExecutor(tefasClient, transactionTemplate);
    }

    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Integer>>getArgument(0).doInTransaction(null));
    }

    @Test
    void should_skipFetch_when_earliestNotBeforeToday() {
        LocalDate today = LocalDate.of(2026, 4, 29);

        FundBulkFetchExecutor.BulkRunResult result = executor.runBackwardWindowed(
                FundType.YAT, today, today, 30, Map.of(), (f, dtos) -> 0);

        assertThat(result.windowsProcessed()).isZero();
        assertThat(result.totalSaved()).isZero();
        verify(tefasClient, never()).bulkFetch(any(), any(), any());
    }

    @Test
    void should_filterUntrackedFunds_when_bulkContainsExtraneousCodes() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 4, 1);
        Fund tracked = fundWith("TI2");
        Map<String, Fund> trackedByCode = new HashMap<>();
        trackedByCode.put(tracked.getFundCode(), tracked);
        when(tefasClient.bulkFetch(FundType.YAT, from, to)).thenReturn(List.of(
                dtoFor("TI2", LocalDate.of(2026, 3, 15)),
                dtoFor("OTHER", LocalDate.of(2026, 3, 15))));
        stubTransactionTemplate();
        AtomicInteger savedFor = new AtomicInteger(0);

        FundBulkFetchExecutor.BulkRunResult result = executor.runBackwardWindowed(
                FundType.YAT, from, to, 31, trackedByCode,
                (fund, dtos) -> { savedFor.set(dtos.size()); return dtos.size(); });

        assertThat(result.windowsProcessed()).isEqualTo(1);
        assertThat(result.totalSaved()).isEqualTo(1);
        assertThat(savedFor.get()).isEqualTo(1);
    }

    @Test
    void should_propagateException_when_bulkFetchThrowsUnexpectedError() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 4, 1);
        Fund tracked = fundWith("TI2");
        Map<String, Fund> trackedByCode = Map.of(tracked.getFundCode(), tracked);
        when(tefasClient.bulkFetch(FundType.YAT, from, to))
                .thenThrow(new RuntimeException("WAF block"));

        assertThatThrownBy(() -> executor.runBackwardWindowed(
                FundType.YAT, from, to, 31, trackedByCode, (f, dtos) -> 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("WAF block");
    }

    @Test
    void should_propagateAndAbort_when_circuitBreakerIsOpen() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 4, 1);
        CircuitBreaker cb = CircuitBreaker.of("tefas", CircuitBreakerConfig.ofDefaults());
        when(tefasClient.bulkFetch(FundType.YAT, from, to))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        assertThatThrownBy(() -> executor.runBackwardWindowed(
                FundType.YAT, from, to, 31, Map.of("X", fundWith("X")), (f, dtos) -> 0))
                .isInstanceOf(CallNotPermittedException.class);
    }

    private Fund fundWith(String code) {
        Fund f = new Fund();
        f.setFundCode(code);
        return f;
    }

    private TefasFundDto dtoFor(String code, LocalDate date) {
        return new TefasFundDto(code, "name " + code,
                date.atStartOfDay(),
                new BigDecimal("1.00"), null, null, null, null);
    }
}
