package com.finance.portfolio.mapper;
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
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public abstract class PortfolioResponseMapper {

    public abstract PortfolioResponse toPortfolioResponse(Portfolio portfolio);

    public PositionResponse toPositionResponse(PortfolioPosition pos,
                                                BigDecimal currentPriceTry,
                                                BigDecimal entryValueTry,
                                                BigDecimal marketValueTry,
                                                BigDecimal pnlTry,
                                                BigDecimal pnlPercent,
                                                String assetName,
                                                String assetImage) {
        return new PositionResponse(
                pos.getId(),
                pos.getAssetType().name(),
                pos.getAssetCode(),
                assetName,
                assetImage,
                pos.getQuantity(),
                pos.getEntryDate(),
                pos.getEntryPrice(),
                currentPriceTry,
                entryValueTry,
                marketValueTry,
                pnlTry,
                pnlPercent
        );
    }

    public PositionResponse toPositionResponseShell(PortfolioPosition pos) {
        return toPositionResponse(pos,
                BigDecimal.ZERO, pos.entryValue(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
    }

    public PortfolioSummaryResponse toSummaryResponse(BigDecimal totalValueTry,
                                                      BigDecimal totalEntryValueTry,
                                                      BigDecimal totalPnlTry,
                                                      BigDecimal pnlPercent,
                                                      BigDecimal dailyPnlTry,
                                                      BigDecimal dailyPnlPercent) {
        return new PortfolioSummaryResponse(
                totalValueTry, totalEntryValueTry, totalPnlTry, pnlPercent,
                dailyPnlTry, dailyPnlPercent
        );
    }

    public AllocationItem toAllocationItem(String label,
                                           String assetType,
                                           BigDecimal valueTry,
                                           BigDecimal percent) {
        return new AllocationItem(label, assetType, valueTry, percent);
    }
}
