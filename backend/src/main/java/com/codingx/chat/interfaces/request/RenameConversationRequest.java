package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;

/**
 * 定义会话重命名请求载体。
 */
public record RenameConversationRequest(
    @NotBlank(message = ErrorMessageCatalog.CHAT_CONVERSATION_TITLE_REQUIRED) String title // 用户提交的新会话标题，不能为空；服务层负责持久化并刷新更新时间。
) {
}
