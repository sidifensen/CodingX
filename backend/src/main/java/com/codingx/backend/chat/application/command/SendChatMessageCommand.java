package com.codingx.backend.chat.application.command;

/**
 * Represents the request or response data carried by SendChatMessageCommand.
 */
public record SendChatMessageCommand(
    Long conversationId, // Related conversation identifier.
    String content // Primary payload content.
) {
}
