package com.finance.portfolio.model;

/**
 * The kind of holdings a portfolio is dedicated to, fixed at creation and never changed afterwards.
 * A {@code SPOT} portfolio holds only spot lots and VIOP derivative positions; a {@code FIXED} portfolio
 * holds only fixed-income instruments (deposits/mevduat and Türkiye Hazine bonds/tahvil). The type is the
 * single source of truth that decides which holdings may be added and which view the frontend renders, so
 * the two product lines stay self-contained rather than co-mingling in one portfolio.
 */
public enum PortfolioType {
    SPOT,
    FIXED
}
