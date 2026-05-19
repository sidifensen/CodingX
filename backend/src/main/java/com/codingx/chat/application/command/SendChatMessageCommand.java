package com.codingx.chat.application.command;

import java.util.List;

/**
 * 定义 SendChatMessageCommand 使用的数据载体。
 */
public record SendChatMessageCommand(
    Long conversationId, // 关联会话标识。
    String content, // 主体内容。
    boolean deepThinking, // 是否开启深度思考。
    List<String> mcpCodes, // 当前会话选择的 MCP 编码。
    List<String> skillCodes, // 当前会话选择的技能编码。
    String repositoryPath // 当前消息显式指定的仓库目录。
) {

    /**
     * 兼容旧调用方，未显式传 MCP 与技能时按空列表处理。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     */
    public SendChatMessageCommand(Long conversationId, String content, boolean deepThinking) {
        this(conversationId, content, deepThinking, List.of(), List.of(), null);
    }

    /**
     * 兼容旧调用方，仅显式传 MCP 编码时技能按空列表处理。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     */
    public SendChatMessageCommand(Long conversationId, String content, boolean deepThinking, List<String> mcpCodes) {
        this(conversationId, content, deepThinking, mcpCodes, List.of(), null);
    }

    /**
     * 兼容旧调用方，同时显式传 MCP 与技能但未传仓库目录时回退为空路径。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     * @param skillCodes 当前会话选择的技能编码。
     */
    public SendChatMessageCommand(
        Long conversationId,
        String content,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes
    ) {
        this(conversationId, content, deepThinking, mcpCodes, skillCodes, null);
    }
}
