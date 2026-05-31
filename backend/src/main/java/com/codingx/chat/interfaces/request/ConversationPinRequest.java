package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotNull;

/**
 * 定义单个会话置顶请求载体。
 */
public record ConversationPinRequest(
    @NotNull(message = ErrorMessageCatalog.CHAT_CONVERSATION_PIN_STATE_REQUIRED) Boolean pinned // 目标置顶状态，true 表示置顶，false 表示取消置顶；不能为空以避免误把缺省值当业务选择。
) {
}
