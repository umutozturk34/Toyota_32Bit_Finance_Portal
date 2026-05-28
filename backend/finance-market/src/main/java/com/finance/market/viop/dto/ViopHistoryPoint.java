package com.finance.market.viop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A single VIOP history bar: its timestamp and close price. */
public record ViopHistoryPoint(LocalDateTime candleDate, BigDecimal close) { }
