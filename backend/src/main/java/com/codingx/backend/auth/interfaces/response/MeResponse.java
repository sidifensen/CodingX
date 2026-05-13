package com.codingx.backend.auth.interfaces.response;
import com.codingx.backend.auth.domain.model.UserType;

/**
 * Represents the request or response data carried by MeResponse.
 */
public record MeResponse(
    Long userId, // userId value.
    String username, // Login username.
    String displayName, // Display name.
    UserType userType // User type.
) {
}
