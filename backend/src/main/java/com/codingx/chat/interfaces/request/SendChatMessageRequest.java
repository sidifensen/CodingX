package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 用户发送聊天消息的 HTTP 请求体。
 */
public record SendChatMessageRequest(
    @NotBlank(message = ErrorMessageCatalog.CHAT_MESSAGE_CONTENT_REQUIRED) String content, // 用户输入正文，不能为空。
    List<String> skillCodes, // 前端显式选择的技能编码；未传时服务层按空列表处理。
    List<Long> attachmentIds // 本次消息关联的附件主键列表；未传时服务层按空列表处理。
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
