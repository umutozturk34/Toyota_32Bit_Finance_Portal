package com.finance.bond.repository;
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

import com.finance.bond.model.Bond;
import com.finance.bond.model.BondType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BondRepository extends JpaRepository<Bond, String>, JpaSpecificationExecutor<Bond> {

    List<Bond> findByBondType(BondType bondType);

    @Query("SELECT b.seriesCode FROM Bond b")
    List<String> findAllSeriesCodes();

    @Query("SELECT b.bondType, COUNT(b) FROM Bond b WHERE b.bondType IS NOT NULL GROUP BY b.bondType ORDER BY b.bondType")
    List<Object[]> countByBondType();
}
