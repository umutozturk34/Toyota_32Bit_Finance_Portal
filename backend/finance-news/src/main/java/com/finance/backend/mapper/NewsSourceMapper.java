package com.finance.backend.mapper;

import com.finance.backend.dto.request.UpsertNewsSourceRequest;
import com.finance.backend.dto.response.NewsSourceResponse;
import com.finance.backend.model.NewsSource;
import com.finance.backend.model.NewsSourceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class NewsSourceMapper {

    @Mapping(target = "sourceType", expression = "java(entity.getSourceType().name())")
    public abstract NewsSourceResponse toResponse(NewsSource entity);

    public abstract List<NewsSourceResponse> toResponses(List<NewsSource> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "url", expression = "java(request.getUrl().trim())")
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "toSourceType")
    @Mapping(target = "enabled", expression = "java(request.getEnabled() != null ? request.getEnabled() : true)")
    @Mapping(target = "sortOrder", expression = "java(request.getSortOrder() != null ? request.getSortOrder() : 0)")
    public abstract NewsSource toEntity(UpsertNewsSourceRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "url", expression = "java(request.getUrl().trim())")
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "toSourceType")
    @Mapping(target = "enabled", expression = "java(request.getEnabled() != null ? request.getEnabled() : true)")
    @Mapping(target = "sortOrder", expression = "java(request.getSortOrder() != null ? request.getSortOrder() : 0)")
    public abstract void updateEntity(UpsertNewsSourceRequest request, @MappingTarget NewsSource entity);

    @Named("toSourceType")
    protected NewsSourceType toSourceType(String type) {
        if (type == null || type.isBlank()) {
            return NewsSourceType.RSS;
        }
        return NewsSourceType.valueOf(type.trim().toUpperCase());
    }
}
