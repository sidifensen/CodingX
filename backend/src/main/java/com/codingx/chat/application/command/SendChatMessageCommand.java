package com.codingx.chat.application.command;

import java.util.List;

/**
 * 定义 SendChatMessageCommand 使用的数据载体。
 */
public record SendChatMessageCommand(
    Long conversationId, // 关联会话标识。
    String content, // 主体内容。
    boolean deepThinking, // 是否开启深度思考。
    List<String> mcpCodes // 当前会话选择的 MCP 编码。
) {

    /**
     * 兼容旧调用方，未显式传技能时按空列表处理。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     */
    public SendChatMessageCommand(Long conversationId, String content, boolean deepThinking) {
        this(conversationId, content, deepThinking, List.of());
    }
}
