package com.finance.backend.util;

import com.finance.backend.client.TefasClient;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.exception.ExternalApiException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public final class TefasHelper {

    private TefasHelper() {}

    public static List<TefasFundDto> fetchTefas(TefasClient client, String fundType,
                                                 String fundCode, LocalDate startDate, LocalDate endDate) {
        List<TefasFundDto> result = client.post(fundType, fundCode, startDate, endDate);
        if (result == null) {
            throw new ExternalApiException("TEFAS",
                    "Non-JSON response for " + fundType + " " + (fundCode != null ? fundCode : "all"));
        }
        return result;
    }

    public static LocalDate findLastBusinessDay(LocalDate from, ZoneId appZone) {
        var istanbulNow = ZonedDateTime.now(appZone);
        LocalDate date = from;
        if (date.equals(istanbulNow.toLocalDate()) && istanbulNow.getHour() < 11) {
            date = date.minusDays(1);
        }
        for (int i = 0; i < 5; i++) {
            var dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                return date;
            }
            date = date.minusDays(1);
        }
        return date;
    }
}
