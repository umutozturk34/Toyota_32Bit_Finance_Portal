package com.finance.app.watchlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "watchlists")
public class OverviewWatchlist {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
