package com.finance.market.viop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ViopHistoryPoint(LocalDateTime candleDate, BigDecimal close) { }
