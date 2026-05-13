package com.codingx.backend.chat.application.command;

public record SendChatMessageCommand(Long conversationId, String content) {
}