package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ViopDisplayNameBuilder {

    private static final DateTimeFormatter EXPIRY_FMT =
            DateTimeFormatter.ofPattern("d MMM yy", new Locale("tr", "TR"));

    private ViopDisplayNameBuilder() { }

    public static String build(ViopContractKind kind, String underlying, ViopOptionSide optionSide,
                                BigDecimal strikePrice, LocalDate expiryDate) {
        if (kind == null) return null;
        StringBuilder sb = new StringBuilder();
        if (underlying != null && !underlying.isBlank()) {
            sb.append(underlying.toUpperCase(Locale.ROOT));
        }
        if (kind == ViopContractKind.OPTION) {
            if (optionSide != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(optionSide == ViopOptionSide.CALL ? "Call" : "Put");
            }
            if (strikePrice != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(strikePrice.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            }
        } else if (kind == ViopContractKind.FUTURE) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("Vadeli");
        }
        if (expiryDate != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(expiryDate.format(EXPIRY_FMT));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
