package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopExerciseStyle;
import com.finance.market.viop.model.ViopOptionSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes VIOP symbols into structured contract attributes. Options encode
 * {@code O_<underlying><E|A><MM><YY><C|P><strike>}; futures encode
 * {@code F_<underlying><MM><YY>}. Returns {@code null} for symbols matching neither shape.
 */
@Component
public class ViopSymbolParser {

    private static final Pattern OPTION_PATTERN = Pattern.compile(
            "^O_([A-Z0-9]+?)([EA])(\\d{2})(\\d{2})([CP])(\\d+(?:\\.\\d+)?)$"
    );

    private static final Pattern FUTURE_PATTERN = Pattern.compile(
            "^F_([A-Z0-9]+?)(\\d{2})(\\d{2})$"
    );

    /** Parses underlying/expiry/strike/side from the symbol, or {@code null} if it is not a VIOP symbol. */
    public Parsed parse(String symbol) {
        if (symbol == null) return null;
        Matcher om = OPTION_PATTERN.matcher(symbol);
        if (om.matches()) {
            String underlying = om.group(1);
            ViopExerciseStyle style = "E".equals(om.group(2)) ? ViopExerciseStyle.EUROPEAN : ViopExerciseStyle.AMERICAN;
            int month = Integer.parseInt(om.group(3));
            int year = 2000 + Integer.parseInt(om.group(4));
            ViopOptionSide side = "C".equals(om.group(5)) ? ViopOptionSide.CALL : ViopOptionSide.PUT;
            String strikeToken = om.group(6);
            BigDecimal strike = new BigDecimal(strikeToken);
            // İş Yatırım encodes currency-option strikes (e.g. O_USDTRYKE0626P46000) as decimal-less integers
            // with 3 implied decimals (46000 → 46.000), whereas equity/index options carry an explicit decimal
            // point (O_AKBNKE0526P45.00, O_XU030E0626P14500.00). Recover the real strike by ÷1000 only for the
            // decimal-less, non-index (currency) form; index/equity tokens keep their explicit value.
            if (strikeToken.indexOf('.') < 0 && !underlying.startsWith("X")) {
                strike = strike.movePointLeft(3);
            }
            return new Parsed(ViopContractKind.OPTION, underlying, year, month, side, strike, style);
        }
        Matcher fm = FUTURE_PATTERN.matcher(symbol);
        if (fm.matches()) {
            String underlying = fm.group(1);
            int month = Integer.parseInt(fm.group(2));
            int year = 2000 + Integer.parseInt(fm.group(3));
            return new Parsed(ViopContractKind.FUTURE, underlying, year, month, null, null, null);
        }
        return null;
    }

    /**
     * VIOP contracts expire on the last BUSINESS day of their expiry month. Used only as a fallback when
     * the metadata feed has not yet supplied the exact expiry, so weekends are stepped back to the prior
     * weekday (close enough for the 1-2 day gap; the feed overwrites it with the real date).
     */
    public LocalDate impliedExpiry(int year, int month) {
        LocalDate last = LocalDate.of(year, month, 1);
        last = last.withDayOfMonth(last.lengthOfMonth());
        while (last.getDayOfWeek() == DayOfWeek.SATURDAY || last.getDayOfWeek() == DayOfWeek.SUNDAY) {
            last = last.minusDays(1);
        }
        return last;
    }

    public record Parsed(
            ViopContractKind kind,
            String underlying,
            int expiryYear,
            int expiryMonth,
            ViopOptionSide optionSide,
            BigDecimal strikePrice,
            ViopExerciseStyle exerciseStyle
    ) { }
}
