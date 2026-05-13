package com.codingx.backend.auth.application.command;

/**
 * Represents the request or response data carried by LoginCommand.
 */
public record LoginCommand(
    String username, // Login username.
    String password // password value.
) {
}
