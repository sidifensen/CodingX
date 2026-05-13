package com.codingx.backend.chat.interfaces.request;

import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequest(@NotBlank(message = "content is required") String content) {
}