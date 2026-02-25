package com.finance.backend.model;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseAsset {
    @Column(name = "name")
    private String name;
    @Column(name = "image")
    private String image;
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}