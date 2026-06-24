package com.finance.news.mapper;


import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.news.model.NewsSource;
import com.finance.news.model.NewsSourceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper between news source entities, API responses, and upsert requests; trims name/URL,
 * applies enabled/sortOrder defaults, and parses the source type leniently.
 */
@Mapper(componentModel = "spring")
public abstract class NewsSourceMapper {

    /** Entity to API response, exposing the source type enum as its name. */
    @Mapping(target = "sourceType", expression = "java(entity.getSourceType().name())")
    public abstract NewsSourceResponse toResponse(NewsSource entity);

    public abstract List<NewsSourceResponse> toResponses(List<NewsSource> entities);

    /** New source from a create request; trims name/URL and defaults enabled→true, sortOrder→0, blank type→RSS. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "url", expression = "java(request.getUrl().trim())")
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "toSourceType")
    @Mapping(target = "enabled", expression = "java(request.getEnabled() != null ? request.getEnabled() : true)")
    @Mapping(target = "sortOrder", expression = "java(request.getSortOrder() != null ? request.getSortOrder() : 0)")
    public abstract NewsSource toEntity(UpsertNewsSourceRequest request);

    /** In-place update of an existing source from a request, applying the same trims/defaults as {@link #toEntity}. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "url", expression = "java(request.getUrl().trim())")
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "toSourceType")
    @Mapping(target = "enabled", expression = "java(request.getEnabled() != null ? request.getEnabled() : true)")
    @Mapping(target = "sortOrder", expression = "java(request.getSortOrder() != null ? request.getSortOrder() : 0)")
    public abstract void updateEntity(UpsertNewsSourceRequest request, @MappingTarget NewsSource entity);

    /** Parses a source-type name (case-insensitive), defaulting blank/null to {@link NewsSourceType#RSS}. */
    @Named("toSourceType")
    protected NewsSourceType toSourceType(String type) {
        if (type == null || type.isBlank()) {
            return NewsSourceType.RSS;
        }
        return NewsSourceType.valueOf(type.trim().toUpperCase());
    }
}
