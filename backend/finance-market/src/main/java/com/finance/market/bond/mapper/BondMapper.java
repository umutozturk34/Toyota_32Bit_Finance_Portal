package com.finance.market.bond.mapper;

import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.bond.model.Bond;
import org.mapstruct.*;

import java.time.LocalDateTime;

/**
 * MapStruct mapper that converts an external {@link BondSnapshotDto} into the persistent
 * {@link Bond} entity. The clean price seeds {@code baseIndex}, the supplied timestamp becomes
 * {@code lastUpdated}, and the derived fields {@code simpleYield}, {@code bondType} and
 * {@code issuer} are left untouched so they can be populated by downstream enrichment.
 */
@Mapper(componentModel = "spring")
public abstract class BondMapper {

    /**
     * Builds a new {@link Bond} entity from a fetched snapshot.
     *
     * @param dto external bond snapshot supplying clean price and core attributes
     * @param now timestamp recorded as the entity's last-updated marker
     * @return a freshly mapped {@link Bond} with derived fields left unset
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract Bond toEntity(BondSnapshotDto dto, LocalDateTime now);

    /**
     * Refreshes an existing {@link Bond} in place from a newer snapshot. The immutable
     * {@code seriesCode} identity field is preserved, and derived fields are not overwritten.
     *
     * @param bond managed entity to update
     * @param dto  external snapshot carrying the new values
     * @param now  timestamp recorded as the entity's last-updated marker
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "seriesCode", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(now)")
    @Mapping(target = "baseIndex", source = "dto.cleanPrice")
    @Mapping(target = "simpleYield", ignore = true)
    @Mapping(target = "bondType", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    public abstract void updateEntity(@MappingTarget Bond bond, BondSnapshotDto dto, LocalDateTime now);
}
