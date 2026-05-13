package com.codingx.backend.auth.application.service;
import com.codingx.backend.auth.domain.model.UserType;

/**
 * Represents the request or response data carried by LoginResult.
 */
public record LoginResult(
    Long userId, // userId value.
    String username, // Login username.
    String displayName, // Display name.
    UserType userType, // User type.
    String token // Access token.
) {
}
