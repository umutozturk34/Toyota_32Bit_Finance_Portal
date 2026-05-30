package com.finance.user.dto;

/** Current user's profile view (identity fields sourced from Keycloak). */
public record ProfileResponse(String username, String firstName, String lastName, String email) {}
