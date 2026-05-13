package com.codingx.backend.chat.application.command;

/**
 * Represents the request or response data carried by CreateConversationCommand.
 */
public record CreateConversationCommand(
    String title // Display title.
) {
}
