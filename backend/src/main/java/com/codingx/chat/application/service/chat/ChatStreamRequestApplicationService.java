package com.codingx.chat.application.service.chat;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.domain.model.ChatMcp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 处理聊天 SSE 入口的应用服务，承接 Controller 下沉的参数解析、工作空间绑定和异步派发编排。
 */
@Service
@RequiredArgsConstructor
public class ChatStreamRequestApplicationService {

    /**
     * 会话应用服务，用于创建会话、校验会话归属和读取公开分享范围内的会话数据。
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天流执行服务，用于将解析后的发送命令派发到后台线程。
     */
    private final ChatStreamExecutionService chatStreamExecutionService;

    /**
     * 工作空间绑定服务，用于本地运行时根据仓库目录创建或复用当前用户工作空间。
     */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * SSE 连接注册表，用于注册会话流连接并推送首个 meta 事件。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * MCP 查询服务，用于缺省 MCP 参数时读取当前启用且可用的 MCP 编码。
     */
    private final ChatMcpQueryService chatMcpQueryService;

    /**
     * 专家配置仓储，用于校验请求传入的专家编码是否存在且已启用。
     */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 打开单次聊天 SSE 流，并把聊天任务派发到后台执行。
     * @param request Controller 收到的查询参数快照。
     * @param userId 当前登录用户标识。
     * @return 已注册的 SSE emitter。
     */
    public SseEmitter openStream(StreamChatRequest request, Long userId) {
        // 步骤 1：解析运行目标和工作空间，必要时根据本地仓库目录绑定工作空间。
        boolean localRuntime = isLocalRuntime(request.runtimeTarget());
        boolean localOnly = false;
        ResolvedWorkspace resolvedWorkspace = resolveWorkspace(
            request.workspaceId(),
            request.repositoryPath(),
            localRuntime
        );

        // 步骤 2：复用既有会话时校验归属；没有会话时创建新会话并绑定工作空间。
        Long actualConversationId = resolveConversationId(
            request.conversationId(),
            resolvedWorkspace.workspaceId(),
            userId,
            localOnly,
            localRuntime
        );
        boolean deepThinkingEnabled = Boolean.TRUE.equals(request.deepThinking());

        // 步骤 3：解析 MCP、结构化消息、技能、专家和附件参数，生成运行时上下文。
        List<String> selectedMcpCodes = resolveMcpCodes(request.mcpCodes());
        StructuredMessageParseResult structuredMessageParseResult = resolveStructuredMessages(request.messages());
        String actualQuestion = StrUtil.blankToDefault(structuredMessageParseResult.content(), request.question());
        List<String> selectedSkillCodes = resolveSkillCodes(request.skillCodes(), structuredMessageParseResult.skillCodes());
        Map<String, String> resolvedSkillPaths = resolveSkillPaths(request.skillPaths());
        String selectedExpertCode = resolveExpertCode(request.expertCode());
        List<Long> selectedAttachmentIds = resolveAttachmentIds(request.attachmentIds());

        // 步骤 4：注册 SSE 连接并先推送 meta 事件，前端据此初始化任务、会话和运行态展示。
        SseEmitter emitter = chatSseRegistry.register(actualConversationId);
        Long taskId = IdUtil.getSnowflakeNextId();
        chatSseRegistry.publish(
            actualConversationId,
            "meta",
            buildMetaPayload(
                actualConversationId,
                deepThinkingEnabled,
                taskId,
                selectedMcpCodes,
                selectedSkillCodes,
                selectedExpertCode,
                selectedAttachmentIds,
                localRuntime,
                localOnly,
                resolvedWorkspace,
                request.repositoryPath()
            )
        );

        // 步骤 5：构建发送命令并交给后台执行服务，Controller 不参与运行时编排。
        chatStreamExecutionService.dispatch(
            taskId,
            buildSendCommand(
                actualConversationId,
                actualQuestion,
                deepThinkingEnabled,
                selectedMcpCodes,
                selectedSkillCodes,
                resolvedSkillPaths,
                selectedExpertCode,
                StrUtil.blankToDefault(resolvedWorkspace.repositoryPath(), request.repositoryPath()),
                selectedAttachmentIds,
                localOnly,
                localRuntime
            ),
            userId
        );
        return emitter;
    }

    /**
     * 订阅既有会话的 SSE 通道，兼容旧的按会话订阅接口。
     * @param conversationId 会话标识。
     * @param userId 当前登录用户标识。
     * @return 已注册的 SSE emitter。
     */
    public SseEmitter subscribeConversation(Long conversationId, Long userId) {
        // 步骤 1：复用消息列表查询做会话归属校验，避免越权订阅他人会话。
        chatConversationApplicationService.listMessages(conversationId, userId);
        // 步骤 2：校验通过后注册 SSE 通道，后续事件由后台执行链路发布。
        return chatSseRegistry.register(conversationId);
    }

    /**
     * 解析本轮请求归属工作空间；本地路径请求即使没带 workspaceId，也要先绑定目录 workspace。
     * @param workspaceId 前端传入的工作空间标识。
     * @param repositoryPath 本地目录路径。
     * @param localRuntime 是否本地运行。
     * @return 归一后的工作空间与目录。
     */
    private ResolvedWorkspace resolveWorkspace(Long workspaceId, String repositoryPath, boolean localRuntime) {
        if (!localRuntime || workspaceId != null || StrUtil.isBlank(repositoryPath)) {
            return new ResolvedWorkspace(workspaceId, StrUtil.trimToNull(repositoryPath));
        }
        // 步骤 1：本地运行且没有显式 workspaceId 时，根据仓库目录绑定当前用户工作空间。
        ChatWorkspaceBindingService.WorkspaceBindingResult bindingResult =
            chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(repositoryPath);
        return new ResolvedWorkspace(bindingResult.workspaceId(), bindingResult.repositoryPath());
    }

    /**
     * 在新建会话和复用既有会话之间解析最终会话标识。
     * @param conversationId 传入会话标识。
     * @param workspaceId 工作空间标识。
     * @param userId 当前用户标识。
     * @param localOnly 是否本地临时运行。
     * @param localRuntime 是否本地运行时。
     * @return 最终会话标识。
     */
    private Long resolveConversationId(
        Long conversationId,
        Long workspaceId,
        Long userId,
        boolean localOnly,
        boolean localRuntime
    ) {
        if (conversationId != null) {
            if (!localOnly) {
                // 步骤 1：复用既有会话时先做 owner 校验，避免越权订阅或发送到他人会话。
                chatConversationApplicationService.listMessages(conversationId, userId);
            }
            return conversationId;
        }
        if (localOnly) {
            // 步骤 2：本地临时模式只需要数值 ID 作为 SSE 路由键，不能据此创建云端会话。
            return IdUtil.getSnowflakeNextId();
        }
        // 步骤 3：新建会话时透传 workspaceId，空值由会话服务按运行目标绑定默认空间。
        ChatConversation conversation = chatConversationApplicationService.createConversation(
            new CreateConversationCommand(null, workspaceId, localRuntime ? "local" : null),
            userId
        );
        return conversation.getId();
    }

    /**
     * 判断当前请求是否显式进入本地运行目标；本地运行目标仍会持久化会话与消息。
     * @param runtimeTarget 前端运行目标。
     * @return 是否本地运行。
     */
    private boolean isLocalRuntime(String runtimeTarget) {
        return StrUtil.equalsIgnoreCase(StrUtil.trimToEmpty(runtimeTarget), "local");
    }

    /**
     * 构建 SSE meta 事件载荷。
     * @param conversationId 最终会话标识。
     * @param deepThinkingEnabled 是否深度思考。
     * @param taskId 后台任务标识。
     * @param selectedMcpCodes 本轮 MCP 编码列表。
     * @param selectedSkillCodes 本轮技能编码列表。
     * @param selectedExpertCode 本轮专家编码。
     * @param selectedAttachmentIds 本轮附件主键列表。
     * @param localRuntime 是否本地运行时。
     * @param localOnly 是否本地临时运行。
     * @param resolvedWorkspace 解析后的工作空间信息。
     * @param repositoryPath 原始仓库路径参数。
     * @return meta 事件载荷。
     */
    private Map<String, Object> buildMetaPayload(
        Long conversationId,
        boolean deepThinkingEnabled,
        Long taskId,
        List<String> selectedMcpCodes,
        List<String> selectedSkillCodes,
        String selectedExpertCode,
        List<Long> selectedAttachmentIds,
        boolean localRuntime,
        boolean localOnly,
        ResolvedWorkspace resolvedWorkspace,
        String repositoryPath
    ) {
        // 步骤 1：基础 meta 字段用于前端绑定任务、能力选择和运行态展示。
        Map<String, Object> metaPayload = new java.util.LinkedHashMap<>();
        metaPayload.put("conversationId", conversationId);
        metaPayload.put("deepThinking", deepThinkingEnabled);
        metaPayload.put("taskId", taskId);
        metaPayload.put("mcpCodes", selectedMcpCodes);
        metaPayload.put("skillCodes", selectedSkillCodes);
        metaPayload.put("expertCode", selectedExpertCode);
        metaPayload.put("attachmentIds", selectedAttachmentIds);
        metaPayload.put("runtimeTarget", localRuntime ? "local" : "cloud");
        metaPayload.put("localOnly", localOnly);
        if (resolvedWorkspace.workspaceId() != null) {
            metaPayload.put("workspaceId", resolvedWorkspace.workspaceId());
        }
        if (StrUtil.isNotBlank(repositoryPath)) {
            // 步骤 2：repositoryPath 优先使用绑定后规范化路径，缺失时回退原始参数。
            metaPayload.put(
                "repositoryPath",
                StrUtil.blankToDefault(resolvedWorkspace.repositoryPath(), StrUtil.trim(repositoryPath))
            );
        }
        return metaPayload;
    }

    /**
     * 根据运行态构建发送命令；默认请求都走云端持久化，localOnly 仅保留给显式临时链路。
     * @param conversationId 最终传输会话标识。
     * @param question 用户问题。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes MCP 编码列表。
     * @param skillCodes 技能编码列表。
     * @param skillPaths 技能本地路径映射。
     * @param expertCode 专家编码。
     * @param repositoryPath 本地仓库目录。
     * @param attachmentIds 附件主键列表。
     * @param localOnly 是否本地临时运行。
     * @param localRuntime 是否本地运行时。
     * @return 发送命令。
     */
    private SendChatMessageCommand buildSendCommand(
        Long conversationId,
        String question,
        boolean deepThinking,
        List<String> mcpCodes,
        List<String> skillCodes,
        Map<String, String> skillPaths,
        String expertCode,
        String repositoryPath,
        List<Long> attachmentIds,
        boolean localOnly,
        boolean localRuntime
    ) {
        String normalizedRepositoryPath = StrUtil.trimToNull(repositoryPath);
        if (localOnly) {
            // 步骤 1：本地临时命令禁止写云端历史，仅使用 conversationId 做 SSE 路由。
            return SendChatMessageCommand.localOnly(
                conversationId,
                question,
                deepThinking,
                mcpCodes,
                skillCodes,
                skillPaths,
                expertCode,
                normalizedRepositoryPath,
                attachmentIds,
                localRuntime
            );
        }
        // 步骤 2：默认构建云端持久化命令，localRuntime 只表示工具运行位置。
        return new SendChatMessageCommand(
            conversationId,
            question,
            deepThinking,
            mcpCodes,
            skillCodes,
            skillPaths,
            expertCode,
            normalizedRepositoryPath,
            attachmentIds,
            false,
            localRuntime
        );
    }

    /**
     * 解析显式 MCP 编码，未传时回退到已启用 MCP 全量列表，保证链路有稳定默认值。
     * @param mcpCodesParam 查询参数字符串。
     * @return 规范化 MCP 编码列表。
     */
    private List<String> resolveMcpCodes(String mcpCodesParam) {
        if (mcpCodesParam != null) {
            // 步骤 1：显式参数按逗号拆分、trim、过滤空值并去重。
            return StrUtil.splitTrim(mcpCodesParam, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        // 步骤 2：未显式传入时回退当前已启用且具备执行器的 MCP。
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
            // 步骤 1：结构化消息中的技能和显式 skillCodes 合并去重，显式技能不会覆盖 slash command 技能。
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
     * 解析 skill 路径映射：将 JSON 字符串解析为 Map。
     * @param skillPathsParam 查询参数字符串，格式为 JSON 对象。
     * @return skill 编码到路径的映射。
     */
    private Map<String, String> resolveSkillPaths(String skillPathsParam) {
        if (StrUtil.isBlank(skillPathsParam)) {
            return Map.of();
        }
        try {
            // 步骤 1：解析前端传入的 JSON 对象，只保留 key 和 value 都有效的路径映射。
            cn.hutool.json.JSONObject jsonObject = cn.hutool.json.JSONUtil.parseObj(skillPathsParam);
            Map<String, String> result = new java.util.HashMap<>();
            for (String key : jsonObject.keySet()) {
                String value = jsonObject.getStr(key);
                if (StrUtil.isNotBlank(value)) {
                    result.put(key.trim(), value.trim());
                }
            }
            return result;
        } catch (Exception exception) {
            // 步骤 2：路径映射解析失败时回退空映射，避免单个脏参数阻断发送链路。
            return Map.of();
        }
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
        // 步骤 1：专家编码必须存在且已启用，否则按未选择专家处理。
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
            // 步骤 1：遍历结构化消息，只识别 slash_command(skill) 和 text 两类运行时需要的数据。
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
            // 步骤 2：技能编码去重后返回，正文保留最后一个 text 内容。
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
        // 步骤 1：按逗号拆分附件 ID，只保留可解析且大于 0 的主键。
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
     * Controller 传入的聊天流请求参数快照。
     * @param question 用户问题原文。
     * @param conversationId 会话标识，可为空。
     * @param workspaceId 工作空间标识，可为空。
     * @param deepThinking 是否启用深度思考。
     * @param mcpCodes 显式 MCP 编码参数，可为空。
     * @param skillCodes 显式技能编码参数，可为空。
     * @param skillPaths 技能本地路径 JSON，可为空。
     * @param expertCode 专家编码，可为空。
     * @param runtimeTarget 运行目标，local 表示本地运行。
     * @param repositoryPath 本地仓库目录，可为空。
     * @param messages 结构化消息 JSON，可为空。
     * @param attachmentIds 附件主键参数，可为空。
     */
    public record StreamChatRequest(
        String question,
        Long conversationId,
        Long workspaceId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String skillPaths,
        String expertCode,
        String runtimeTarget,
        String repositoryPath,
        String messages,
        String attachmentIds
    ) {
    }

    /**
     * 本轮请求解析后的工作空间归属。
     * @param workspaceId 工作空间标识，可为空。
     * @param repositoryPath 规范化后的本地目录路径，可为空。
     */
    private record ResolvedWorkspace(Long workspaceId, String repositoryPath) {
    }

    /**
     * 结构化消息解析结果。
     * @param skillCodes 解析出的技能编码列表。
     * @param content 解析出的最终问题正文。
     */
    private record StructuredMessageParseResult(List<String> skillCodes, String content) {
    }
}
