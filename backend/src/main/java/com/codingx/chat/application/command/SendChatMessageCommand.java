package com.codingx.chat.application.command;

import java.util.List;
import java.util.Map;

/**
 * 定义 SendChatMessageCommand 使用的数据载体。
 */
public record SendChatMessageCommand(
    Long conversationId, // 关联会话标识。
    String content, // 主体内容。
    boolean deepThinking, // 是否开启深度思考。
    List<String> mcpCodes, // 当前会话选择的 MCP 编码。
    List<String> skillCodes, // 当前会话选择的技能编码。
    Map<String, String> skillPaths, // skill 编码到本地路径的映射。
    String expertCode, // 当前消息选择的专家编码。
    String repositoryPath, // 当前消息显式指定的仓库目录。
    List<Long> attachmentIds, // 当前消息关联附件主键。
    boolean localOnly, // 是否仅作为本地临时会话执行，禁止写入云端会话与消息表。
    boolean localRuntime // 是否本地运行时。
) {

    /**
     * 兼容旧调用方，未显式声明本地模式时默认走云端持久化语义。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     * @param skillCodes 当前会话选择的技能编码。
     * @param expertCode 当前消息选择的专家编码。
     * @param repositoryPath 当前消息显式指定的仓库目录。
     * @param attachmentIds 当前消息关联附件主键。
     */
    public SendChatMessageCommand(
        Long conversationId,
        String content,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        String expertCode,
        String repositoryPath,
        List<Long> attachmentIds
    ) {
        this(conversationId, content, deepThinking, mcpCodes, skillCodes, Map.of(), expertCode, repositoryPath, attachmentIds, false, false);
    }

    /**
     * 兼容旧调用方，未显式传 MCP 与技能时按空列表处理。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     */
    public SendChatMessageCommand(Long conversationId, String content, boolean deepThinking) {
        this(conversationId, content, deepThinking, List.of(), List.of(), Map.of(), null, null, List.of(), false, false);
    }

    /**
     * 兼容旧调用方，仅显式传 MCP 编码时技能按空列表处理。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     */
    public SendChatMessageCommand(Long conversationId, String content, boolean deepThinking, List<String> mcpCodes) {
        this(conversationId, content, deepThinking, mcpCodes, List.of(), Map.of(), null, null, List.of(), false, false);
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
        this(conversationId, content, deepThinking, mcpCodes, skillCodes, Map.of(), null, null, List.of(), false, false);
    }

    /**
     * 兼容旧调用方，显式传仓库目录但未传附件时回退为空附件列表。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     * @param skillCodes 当前会话选择的技能编码。
     * @param repositoryPath 当前消息显式指定的仓库目录。
     */
    public SendChatMessageCommand(
        Long conversationId,
        String content,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        String repositoryPath
    ) {
        this(conversationId, content, deepThinking, mcpCodes, skillCodes, Map.of(), null, repositoryPath, List.of(), false, false);
    }

    /**
     * 兼容旧调用方，显式传专家与仓库目录但未传附件时回退为空附件列表。
     * @param conversationId 会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     * @param skillCodes 当前会话选择的技能编码。
     * @param expertCode 当前消息选择的专家编码。
     * @param repositoryPath 当前消息显式指定的仓库目录。
     */
    public SendChatMessageCommand(
        Long conversationId,
        String content,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        String expertCode,
        String repositoryPath
    ) {
        this(conversationId, content, deepThinking, mcpCodes, skillCodes, Map.of(), expertCode, repositoryPath, List.of(), false, false);
    }

    /**
     * 构建本地临时运行命令；该命令允许后端使用临时 conversationId 做 SSE 路由，但禁止写云端历史。
     * @param conversationId 临时传输会话标识。
     * @param content 主体内容。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 当前会话选择的 MCP 编码。
     * @param skillCodes 当前会话选择的技能编码。
     * @param skillPaths skill 编码到本地路径的映射。
     * @param expertCode 当前消息选择的专家编码。
     * @param repositoryPath 当前消息显式指定的仓库目录。
     * @param attachmentIds 当前消息关联附件主键。
     * @param localRuntime 是否本地运行时。
     * @return 本地临时运行命令。
     */
    public static SendChatMessageCommand localOnly(
        Long conversationId,
        String content,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        Map<String, String> skillPaths,
        String expertCode,
        String repositoryPath,
        List<Long> attachmentIds,
        boolean localRuntime
    ) {
        return new SendChatMessageCommand(
            conversationId,
            content,
            deepThinking,
            mcpCodes,
            skillCodes,
            skillPaths,
            expertCode,
            repositoryPath,
            attachmentIds,
            true,
            localRuntime
        );
    }
}
