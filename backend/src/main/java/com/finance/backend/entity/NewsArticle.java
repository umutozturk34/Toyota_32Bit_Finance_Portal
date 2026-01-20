package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    @Column(length = 5000)
    private String content;
    
    @Column(nullable = false, length = 2048)
    private String url;
    
    @Column(length = 2048)
    private String imageUrl;
    
    private String source;
    
    private String author;
    
    @Column(length = 50)
    private String category; // CRYPTO, ISTANBUL_STOCK, FOREX_METALS, US_STOCK, GENERAL
    
    @Column(nullable = false)
    private LocalDateTime publishedAt;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
