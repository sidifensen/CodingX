package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.common.idempotent.IdempotentSubmit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责处理 ChatStreamController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    /**
     * 会话应用服务依赖。
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天应用服务依赖。
     */
    private final ChatStreamExecutionService chatStreamExecutionService;

    /**
     * ChatSseRegistry 依赖。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * 技能配置仓储依赖。
     */
    private final ChatMcpQueryService chatMcpQueryService;
    private final ChatSkillRepository chatSkillRepository;
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 建立单次 SSE 聊天入口，并在同一请求内完成注册与消息发送。
     * @param question 用户问题。
     * @param conversationId 会话标识，可为空。
     * @param deepThinking 是否启用深度思考。
     * @param mcpCodes 显式传入的 MCP 编码，可为空。
     * @return SSE emitter。
     */
    @GetMapping("/stream")
    @IdempotentSubmit(
        key = "T(cn.dev33.satoken.stp.StpUtil).getLoginIdAsLong() + ':' + (#conversationId == null ? 'new' : #conversationId)",
        message = "当前会话处理中，请稍后再发起新的对话",
        code = "CHAT_STREAM_DUPLICATE",
        waitTimeMs = 0,
        leaseTimeMs = 30000
    )
    public SseEmitter streamChat(
        @RequestParam String question,
        @RequestParam(required = false) Long conversationId,
        @RequestParam(required = false) Long workspaceId,
        @RequestParam(required = false) Boolean deepThinking,
        @RequestParam(required = false) String mcpCodes,
        @RequestParam(required = false) String skillCodes,
        @RequestParam(required = false) String expertCode,
        @RequestParam(required = false) String runtimeTarget,
        @RequestParam(required = false) String repositoryPath,
        @RequestParam(required = false) String messages,
        @RequestParam(required = false) String attachmentIds
    ) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean localOnly = isLocalRuntime(runtimeTarget);
        Long actualConversationId = resolveConversationId(conversationId, workspaceId, userId, localOnly);
        boolean deepThinkingEnabled = Boolean.TRUE.equals(deepThinking);
        // 步骤：同一入口同时支持 MCP 与技能绑定，分别解析后传入运行时，避免语义混淆。
        List<String> selectedMcpCodes = resolveMcpCodes(mcpCodes);
        StructuredMessageParseResult structuredMessageParseResult = resolveStructuredMessages(messages);
        String actualQuestion = StrUtil.blankToDefault(structuredMessageParseResult.content(), question);
        List<String> selectedSkillCodes = resolveSkillCodes(skillCodes, structuredMessageParseResult.skillCodes());
        String selectedExpertCode = resolveExpertCode(expertCode);
        List<Long> selectedAttachmentIds = resolveAttachmentIds(attachmentIds);
        SseEmitter emitter = chatSseRegistry.register(actualConversationId);
        Long taskId = cn.hutool.core.util.IdUtil.getSnowflakeNextId();
        java.util.Map<String, Object> metaPayload = new java.util.LinkedHashMap<>();
        metaPayload.put("conversationId", actualConversationId);
        metaPayload.put("deepThinking", deepThinkingEnabled);
        metaPayload.put("taskId", taskId);
        metaPayload.put("mcpCodes", selectedMcpCodes);
        metaPayload.put("skillCodes", selectedSkillCodes);
        metaPayload.put("expertCode", selectedExpertCode);
        metaPayload.put("attachmentIds", selectedAttachmentIds);
        metaPayload.put("runtimeTarget", localOnly ? "local" : "cloud");
        metaPayload.put("localOnly", localOnly);
        if (StrUtil.isNotBlank(repositoryPath)) {
            metaPayload.put("repositoryPath", StrUtil.trim(repositoryPath));
        }
        chatSseRegistry.publish(actualConversationId, "meta", metaPayload);
        chatStreamExecutionService.dispatch(
            taskId,
            buildSendCommand(
                actualConversationId,
                actualQuestion,
                deepThinkingEnabled,
                selectedMcpCodes,
                selectedSkillCodes,
                selectedExpertCode,
                repositoryPath,
                selectedAttachmentIds,
                localOnly
            ),
            userId
        );
        return emitter;
    }

    /**
     * 兼容旧调用签名，未显式传 MCP 参数时自动回退到默认 MCP 集合。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(String question, Long conversationId, Boolean deepThinking) {
        return streamChat(question, conversationId, null, deepThinking, null, null, null, null, null, null, null);
    }

    /**
     * 兼容旧的完整参数调用签名；旧调用方不传 runtimeTarget 时继续按云端持久化处理。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param workspaceId 工作空间标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param expertCode 专家编码。
     * @param repositoryPath 当前仓库路径。
     * @param messages 结构化消息 JSON。
     * @param attachmentIds 附件主键列表。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Long workspaceId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String expertCode,
        String repositoryPath,
        String messages,
        String attachmentIds
    ) {
        return streamChat(
            question,
            conversationId,
            workspaceId,
            deepThinking,
            mcpCodes,
            skillCodes,
            expertCode,
            null,
            repositoryPath,
            messages,
            attachmentIds
        );
    }

    /**
     * 兼容旧调用签名，允许显式 MCP 与技能编码。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, null, null, null);
    }

    /**
     * 兼容传入结构化消息但未显式提供 repositoryPath 的调用签名。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param messages 结构化消息 JSON。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String messages
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, null, messages, null);
    }

    /**
     * 兼容显式传入 repositoryPath 与结构化 messages 但未传附件参数的调用签名。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param repositoryPath 当前仓库路径。
     * @param messages 结构化消息 JSON。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String repositoryPath,
        String messages
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, repositoryPath, messages, null);
    }

    /**
     * 兼容旧的按会话订阅流接口，避免阶段切换时现有调用完全失效。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/conversations/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        StpUtil.checkLogin();
        chatConversationApplicationService.listMessages(conversationId, StpUtil.getLoginIdAsLong());
        return chatSseRegistry.register(conversationId);
    }

    /**
     * 在新建会话和复用既有会话之间解析最终会话标识。
     * @param conversationId 传入会话标识。
     * @param userId 当前用户标识。
     * @return 最终会话标识。
     */
    private Long resolveConversationId(Long conversationId, Long workspaceId, Long userId, boolean localOnly) {
        if (conversationId != null) {
            if (!localOnly) {
                // 步骤：复用既有会话时先做 owner 校验，避免越权订阅或发送到他人会话。
                chatConversationApplicationService.listMessages(conversationId, userId);
            }
            return conversationId;
        }
        if (localOnly) {
            // 本地模式只需要临时数值 ID 作为 SSE 路由键，不能据此创建云端会话。
            return IdUtil.getSnowflakeNextId();
        }
        // 新建会话时透传 workspaceId：为空走默认云端空间，不为空绑定本地空间。
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(null, workspaceId),
            userId
        );
        return conversation.getId();
    }

    /**
     * 判断当前请求是否显式进入本地临时运行态。
     * @param runtimeTarget 前端运行目标。
     * @return 是否本地临时模式。
     */
    private boolean isLocalRuntime(String runtimeTarget) {
        return StrUtil.equalsIgnoreCase(StrUtil.trimToEmpty(runtimeTarget), "local");
    }

    /**
     * 根据运行态构建发送命令；本地模式必须携带 localOnly 语义，供下游跳过云端持久化。
     * @param conversationId 最终传输会话标识。
     * @param question 用户问题。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes MCP 编码列表。
     * @param skillCodes 技能编码列表。
     * @param expertCode 专家编码。
     * @param repositoryPath 本地仓库目录。
     * @param attachmentIds 附件主键列表。
     * @param localOnly 是否本地临时运行。
     * @return 发送命令。
     */
    private SendChatMessageCommand buildSendCommand(
        Long conversationId,
        String question,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        String expertCode,
        String repositoryPath,
        List<Long> attachmentIds,
        boolean localOnly
    ) {
        String normalizedRepositoryPath = StrUtil.trimToNull(repositoryPath);
        if (localOnly) {
            return SendChatMessageCommand.localOnly(
                conversationId,
                question,
                deepThinking,
                mcpCodes,
                skillCodes,
                expertCode,
                normalizedRepositoryPath,
                attachmentIds
            );
        }
        return new SendChatMessageCommand(
            conversationId,
            question,
            deepThinking,
            mcpCodes,
            skillCodes,
            expertCode,
            normalizedRepositoryPath,
            attachmentIds
        );
    }

    /**
     * 解析显式 MCP 编码，未传时回退到已启用 MCP 全量列表，保证链路有稳定默认值。
     * @param mcpCodesParam 查询参数字符串。
     * @return 规范化 MCP 编码列表。
     */
    private List<String> resolveMcpCodes(String mcpCodesParam) {
        if (mcpCodesParam != null) {
            return StrUtil.splitTrim(mcpCodesParam, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        return chatMcpQueryService.listEnabledMcps().stream()
            .filter(mcp -> mcp.getAvailable() == null || Boolean.TRUE.equals(mcp.getAvailable()))
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /**
     * 解析技能编码：优先合并结构化消息与显式参数；两者都缺失时返回空列表。
     * 关键约束：仅在前端显式选择技能时注入，避免默认全量技能污染对话上下文。
     * @param skillCodesParam 查询参数字符串。
     * @param parsedSkillCodes 结构化消息解析出的技能编码。
     * @return 规范化技能编码列表。
     */
    private List<String> resolveSkillCodes(String skillCodesParam, List<String> parsedSkillCodes) {
        List<String> explicitSkillCodes = null;
        if (skillCodesParam != null) {
            explicitSkillCodes = StrUtil.splitTrim(skillCodesParam, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        if ((parsedSkillCodes != null && !parsedSkillCodes.isEmpty()) || explicitSkillCodes != null) {
            return Stream.concat(
                    parsedSkillCodes == null ? Stream.empty() : parsedSkillCodes.stream(),
                    explicitSkillCodes == null ? Stream.empty() : explicitSkillCodes.stream()
                )
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 解析专家编码：仅允许启用专家进入运行态，非法编码直接忽略，避免脏数据污染上下文。
     * @param expertCodeParam 查询参数字符串。
     * @return 规范化专家编码。
     */
    private String resolveExpertCode(String expertCodeParam) {
        String normalizedExpertCode = StrUtil.trimToNull(expertCodeParam);
        if (normalizedExpertCode == null) {
            return null;
        }
        ChatExpert expert = chatExpertRepository.findByExpertCode(normalizedExpertCode);
        if (expert == null || expert.getEnabled() == null || expert.getEnabled() != 1) {
            return null;
        }
        return expert.getExpertCode();
    }

    /**
     * 解析结构化消息，仅提取技能命令与正文文本，避免技能语法污染自然语言内容。
     * @param messagesParam 前端传入的结构化消息 JSON。
     * @return 技能与文本解析结果。
     */
    private StructuredMessageParseResult resolveStructuredMessages(String messagesParam) {
        if (StrUtil.isBlank(messagesParam)) {
            return new StructuredMessageParseResult(List.of(), null);
        }
        try {
            cn.hutool.json.JSONArray messagesArray = cn.hutool.json.JSONUtil.parseArray(messagesParam);
            List<String> parsedSkillCodes = new java.util.ArrayList<>();
            String parsedTextContent = null;
            for (Object messageItem : messagesArray) {
                cn.hutool.json.JSONObject messageObject = cn.hutool.json.JSONUtil.parseObj(messageItem);
                String type = StrUtil.trimToEmpty(messageObject.getStr("type"));
                cn.hutool.json.JSONObject dataObject = messageObject.getJSONObject("data");
                if (dataObject == null) {
                    continue;
                }
                if ("slash_command".equalsIgnoreCase(type)) {
                    String commandType = StrUtil.trimToEmpty(dataObject.getStr("command_type"));
                    String command = StrUtil.trimToEmpty(dataObject.getStr("command"));
                    if ("skill".equalsIgnoreCase(commandType) && StrUtil.isNotBlank(command)) {
                        parsedSkillCodes.add(command);
                    }
                    continue;
                }
                if ("text".equalsIgnoreCase(type)) {
                    String content = StrUtil.trimToNull(dataObject.getStr("content"));
                    if (content != null) {
                        parsedTextContent = content;
                    }
                }
            }
            List<String> normalizedSkillCodes = parsedSkillCodes.stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
            return new StructuredMessageParseResult(normalizedSkillCodes, parsedTextContent);
        } catch (Exception ignored) {
            // 结构化消息解析失败时回退到普通文本模式，避免阻断发送链路。
            return new StructuredMessageParseResult(List.of(), null);
        }
    }

    /**
     * 解析前端传入的附件主键列表，忽略非法值并按出现顺序去重。
     * @param attachmentIdsParam 查询参数字符串。
     * @return 规范化附件主键列表。
     */
    private List<Long> resolveAttachmentIds(String attachmentIdsParam) {
        if (attachmentIdsParam == null) {
            return List.of();
        }
        return StrUtil.splitTrim(attachmentIdsParam, ',').stream()
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .map(value -> {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    return null;
                }
            })
            .filter(id -> id != null && id > 0)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 结构化消息解析结果。
     * @param skillCodes 解析出的技能编码列表。
     * @param content 解析出的最终问题正文。
     */
    private record StructuredMessageParseResult(List<String> skillCodes, String content) {
    }
}
