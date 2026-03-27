package com.finance.backend.mapper;

import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.model.Bond;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class BondMapper {

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract Bond toEntity(BondSnapshotDto dto, LocalDateTime now);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "seriesCode", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract void updateEntity(@MappingTarget Bond bond, BondSnapshotDto dto, LocalDateTime now);
}
