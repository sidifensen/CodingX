package com.codingx.backend.chat.interfaces.request;

/**
 * Represents the request or response data carried by CreateConversationRequest.
 */
public record CreateConversationRequest(
    String title // Display title.
) {
}
