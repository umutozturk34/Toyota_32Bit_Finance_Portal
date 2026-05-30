package com.finance.news.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Admin payload to create or update a news source; name and URL are required, the rest default. */
@Getter
@Setter
public class UpsertNewsSourceRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String url;

    private String sourceType = "RSS";

    private String defaultCategory;

    private Boolean enabled = true;

    private Integer sortOrder = 0;
}
