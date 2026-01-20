package com.finance.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class NewsApiResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String status;
    
    private Integer totalResults;
    
    private List<Article> articles;
    
    @Data
    public static class Article implements Serializable {
        private static final long serialVersionUID = 1L;
        private Source source;
        private String author;
        private String title;
        private String description;
        private String url;
        private String urlToImage;
        private String publishedAt;
        private String content;
    }
    
    @Data
    public static class Source implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
    }
}
