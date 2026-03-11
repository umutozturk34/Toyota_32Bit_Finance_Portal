package com.finance.backend.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.finance.backend.dto.external.TcmbRateDto;

import java.util.List;

@JacksonXmlRootElement(localName = "Tarih_Date")
@JsonIgnoreProperties(ignoreUnknown = true)
public record TcmbXmlResponse(
        @JacksonXmlProperty(localName = "Currency")
        @JacksonXmlElementWrapper(useWrapping = false)
        List<TcmbRateDto> currencies
) {}
