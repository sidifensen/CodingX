package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 定义批量会话操作请求载体。
 */
public record BatchUpdateConversationRequest(
    @NotEmpty(message = ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_IDS_REQUIRED) List<Long> conversationIds,
    boolean pinned
) {
}
