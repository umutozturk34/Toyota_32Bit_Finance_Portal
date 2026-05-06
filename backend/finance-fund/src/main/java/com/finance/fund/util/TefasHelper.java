package com.finance.fund.util;
import com.finance.common.service.MarketSnapshotProcessor;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TefasHelper {

    private TefasHelper() {}

    public static LocalDate findLastBusinessDay(LocalDate from, ZoneId appZone, int eodCutoverHour) {
        var istanbulNow = ZonedDateTime.now(appZone);
        LocalDate date = from;
        if (date.equals(istanbulNow.toLocalDate()) && istanbulNow.getHour() < eodCutoverHour) {
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
