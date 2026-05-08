package com.finance.news.dto.request;

import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

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
