package com.finance.backend.repository;

import com.finance.backend.model.Commodity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommodityRepository extends JpaRepository<Commodity, String>, JpaSpecificationExecutor<Commodity> {

    List<Commodity> findAllByOrderByCommodityCodeAsc();
}
