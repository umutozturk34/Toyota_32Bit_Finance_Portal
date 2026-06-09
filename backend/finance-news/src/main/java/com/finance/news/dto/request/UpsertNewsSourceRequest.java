package com.finance.news.dto.request;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Admin payload to create or update a news source; name and URL are required, the rest default. */
@Getter
@Setter
public class UpsertNewsSourceRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 1024)
    @Pattern(regexp = "^https?://.*")
    private String url;

    @Size(max = 30)
    private String sourceType = "RSS";

    @Size(max = 64)
    private String defaultCategory;

    private Boolean enabled = true;

    @Min(0)
    @Max(100000)
    private Integer sortOrder = 0;
}
