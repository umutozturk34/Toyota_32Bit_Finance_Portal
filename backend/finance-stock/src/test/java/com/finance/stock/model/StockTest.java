package com.finance.stock.model;
import com.finance.stock.model.Stock;

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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockTest {

    private Stock createStock(String current, String prevClose, String open, String high, String low) {
        Stock stock = new Stock();
        stock.setCurrentPrice(current != null ? new BigDecimal(current) : null);
        stock.setPreviousClose(prevClose != null ? new BigDecimal(prevClose) : null);
        stock.setOpenPrice(open != null ? new BigDecimal(open) : null);
        stock.setDayHigh(high != null ? new BigDecimal(high) : null);
        stock.setDayLow(low != null ? new BigDecimal(low) : null);
        return stock;
    }

    @Test
    void scaleFieldsScalesAllPriceFields() {
        Stock stock = createStock("55.123456", "50.987654", "51.111111", "56.666666", "49.333333");

        stock.scaleFields(4);

        assertThat(stock.getCurrentPrice().scale()).isEqualTo(4);
        assertThat(stock.getPreviousClose().scale()).isEqualTo(4);
        assertThat(stock.getOpenPrice().scale()).isEqualTo(4);
        assertThat(stock.getDayHigh().scale()).isEqualTo(4);
        assertThat(stock.getDayLow().scale()).isEqualTo(4);
    }

    @Test
    void applyChangeComputesAmountAndPercentFromPriceAndPreviousClose() {
        Stock stock = createStock("55", "50", "51", "56", "49");

        stock.applyChange(stock.getCurrentPrice(), stock.getPreviousClose(), 4);

        assertThat(stock.getChangeAmount()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(stock.getChangePercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void applyChangeLeavesFieldsNullWhenPreviousCloseMissing() {
        Stock stock = createStock("55", null, "51", "56", "49");

        stock.applyChange(stock.getCurrentPrice(), stock.getPreviousClose(), 4);

        assertThat(stock.getChangeAmount()).isNull();
        assertThat(stock.getChangePercent()).isNull();
    }

    @Test
    void scaleFieldsLeavesNullPriceFieldsNull() {
        Stock stock = createStock("55", "50", null, null, null);

        stock.scaleFields(4);

        assertThat(stock.getOpenPrice()).isNull();
        assertThat(stock.getDayHigh()).isNull();
        assertThat(stock.getDayLow()).isNull();
    }
}
