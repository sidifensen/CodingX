package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import java.util.Map;
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
    private final ChatMcpRepository chatMcpRepository;

    /**
     * 建立单次 SSE 聊天入口，并在同一请求内完成注册与消息发送。
     * @param question 用户问题。
     * @param conversationId 会话标识，可为空。
     * @param deepThinking 是否启用深度思考。
     * @param mcpCodes 显式传入的 MCP 编码，可为空。
     * @return SSE emitter。
     */
    @GetMapping("/stream")
    public SseEmitter streamChat(
        @RequestParam String question,
        @RequestParam(required = false) Long conversationId,
        @RequestParam(required = false) Boolean deepThinking,
        @RequestParam(required = false) String mcpCodes,
        @RequestParam(required = false) String skillCodes
    ) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        Long actualConversationId = resolveConversationId(conversationId, userId);
        boolean deepThinkingEnabled = Boolean.TRUE.equals(deepThinking);
        List<String> selectedMcpCodes = resolveMcpCodes(mcpCodes, skillCodes);
        SseEmitter emitter = chatSseRegistry.register(actualConversationId);
        Long taskId = cn.hutool.core.util.IdUtil.getSnowflakeNextId();
        chatSseRegistry.publish(actualConversationId, "meta", Map.of(
            "conversationId", actualConversationId,
            "deepThinking", deepThinkingEnabled,
            "taskId", taskId,
            "mcpCodes", selectedMcpCodes
        ));
        chatStreamExecutionService.dispatch(new SendChatMessageCommand(actualConversationId, question, deepThinkingEnabled, selectedMcpCodes), userId);
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
        return streamChat(question, conversationId, deepThinking, null, null);
    }

    /**
     * 兼容旧的按会话订阅流接口，避免阶段切换时现有调用完全失效。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/conversations/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        StpUtil.checkLogin();
        return chatSseRegistry.register(conversationId);
    }

    /**
     * 在新建会话和复用既有会话之间解析最终会话标识。
     * @param conversationId 传入会话标识。
     * @param userId 当前用户标识。
     * @return 最终会话标识。
     */
    private Long resolveConversationId(Long conversationId, Long userId) {
        if (conversationId != null) {
            return conversationId;
        }
        ChatConversation conversation = chatConversationApplicationService.createConversation(new CreateConversationCommand(null), userId);
        return conversation.getId();
    }

    /**
     * 解析显式 MCP 编码，未传时回退到已启用 MCP 全量列表，保证链路有稳定默认值。
     * @param mcpCodesParam 查询参数字符串。
     * @return 规范化 MCP 编码列表。
     */
    private List<String> resolveMcpCodes(String mcpCodesParam, String skillCodesParam) {
        // 步骤：兼容前端新增 skillCodes 参数；显式传入时优先使用，便于 slash 技能选择直通运行时绑定。
        if (skillCodesParam != null) {
            return StrUtil.splitTrim(skillCodesParam, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        if (mcpCodesParam != null) {
            return StrUtil.splitTrim(mcpCodesParam, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        }
        return chatMcpRepository.findAllEnabled().stream()
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .toList();
    }
}
