package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 定义 SendChatMessageRequest 使用的数据载体。
 */
public record SendChatMessageRequest(
    @NotBlank(message = ErrorMessageCatalog.CHAT_MESSAGE_CONTENT_REQUIRED) String content, // 主体内容。
    List<String> skillCodes, // 当前会话选择的技能编码。
    List<Long> attachmentIds // 关联附件主键列表。
) {

    /**
     * 兼容旧调用方，未显式传技能列表时按空列表处理。
     * @param content 主体内容。
     * @param attachmentIds 关联附件主键列表。
     */
    public SendChatMessageRequest(String content, List<Long> attachmentIds) {
        this(content, List.of(), attachmentIds);
    }
}
