package com.finance.market.fund.repository;
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

import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundRepository extends JpaRepository<Fund, String>, JpaSpecificationExecutor<Fund> {
    List<Fund> findByFundType(FundType fundType);

    @Query("SELECT f.fundCode FROM Fund f")
    List<String> findAllFundCodes();

    @Query("SELECT f.fundType, COUNT(f) FROM Fund f WHERE f.fundType IS NOT NULL GROUP BY f.fundType ORDER BY f.fundType")
    List<Object[]> countByFundType();
}
