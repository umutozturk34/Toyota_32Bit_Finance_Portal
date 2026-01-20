package com.finance.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class AlphaVantageResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("Global Quote")
    private GlobalQuote globalQuote;
    
    @JsonProperty("Information")
    private String information;
    
    @JsonProperty("Note")
    private String note;
    
    @Data
    public static class GlobalQuote implements Serializable {
        private static final long serialVersionUID = 1L;
        @JsonProperty("01. symbol")
        private String symbol;
        
        @JsonProperty("02. open")
        private String open;
        
        @JsonProperty("03. high")
        private String high;
        
        @JsonProperty("04. low")
        private String low;
        
        @JsonProperty("05. price")
        private String price;
        
        @JsonProperty("06. volume")
        private String volume;
        
        @JsonProperty("08. previous close")
        private String previousClose;
        
        @JsonProperty("09. change")
        private String change;
        
        @JsonProperty("10. change percent")
        private String changePercent;
    }
}
