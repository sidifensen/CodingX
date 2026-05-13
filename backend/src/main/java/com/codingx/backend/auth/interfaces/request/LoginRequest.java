package com.codingx.backend.auth.interfaces.request;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the request or response data carried by LoginRequest.
 */
public record LoginRequest(
    @NotBlank(message = "username is required") String username, // Login username.
    @NotBlank(message = "password is required") String password // password value.
) {
}
