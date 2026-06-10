package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to add or update a spot lot. {@code priceCurrency} is the currency of the supplied
 * entry/exit prices; the server converts them to TRY at the respective dates before persisting
 * (null/blank means the prices are already TRY).
 *
 * <p>Bounds mirror the persisted columns + the client guards: codes are length/charset capped, amounts are
 * positive, capped and digit-bounded to the entity precision (quantity 19,8 / prices 19,4). Dates carry no
 * {@code @PastOrPresent} on purpose — they are {@link LocalDateTime} and a same-day entry can hold a later
 * time-of-day than the server clock, which would falsely read as "future"; the UI caps the picker to today.
 */
public record PositionRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "^[A-Za-z]{1,16}$") String assetType,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9._=-]{1,32}$") String assetCode,
        @NotNull @Positive @DecimalMax("1000000000") @Digits(integer = 11, fraction = 8) BigDecimal quantity,
        @NotNull LocalDateTime entryDate,
        @NotNull @Positive @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 8) BigDecimal entryPrice,
        LocalDateTime exitDate,
        @Positive @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 8) BigDecimal exitPrice,
        @Pattern(regexp = "^[A-Z]{3}$") String priceCurrency
) {
    public PositionRequest(String assetType, String assetCode, BigDecimal quantity,
                           LocalDateTime entryDate, BigDecimal entryPrice) {
        this(assetType, assetCode, quantity, entryDate, entryPrice, null, null, null);
    }

    public PositionRequest(String assetType, String assetCode, BigDecimal quantity,
                           LocalDateTime entryDate, BigDecimal entryPrice,
                           LocalDateTime exitDate, BigDecimal exitPrice) {
        this(assetType, assetCode, quantity, entryDate, entryPrice, exitDate, exitPrice, null);
    }
}
