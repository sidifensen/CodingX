package com.codingx.backend.chat.interfaces.request;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the request or response data carried by SendChatMessageRequest.
 */
public record SendChatMessageRequest(
    @NotBlank(message = "content is required") String content // Primary payload content.
) {
}
