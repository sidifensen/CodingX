package com.codingx.chat.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 ChatMessage 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 关联会话标识。
     */
    private Long conversationId;

    /**
     * role 字段。
     */
    private ChatMessageRole role;

    /**
     * 所属执行记录标识。
     */
    private Long runId;

    /**
     * 主体内容。
     */
    private String content;

    /**
     * 深度思考内容。
     */
    private String thinkingContent;

    /**
     * 深度思考耗时（秒）。
     */
    private Integer thinkingDuration;

    /**
     * 命中的意图编码。
     */
    private String intentCode;

    /**
     * 当前状态值。
     */
    private ChatMessageStatus status;

    /**
     * 提供方标识。
     */
    private String provider;

    /**
     * 模型标识。
     */
    private String model;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 执行 userMessage 定义的处理逻辑。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     * @return 输入参数。
     */
    public static ChatMessage userMessage(Long conversationId, String content) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.USER, content, ChatMessageStatus.COMPLETED, null, null, null);
    }

    /**
     * 执行 assistantMessage 定义的处理逻辑。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     * @param status 输入参数。
     * @param provider 输入参数。
     * @param model 输入参数。
     * @param errorMessage 输入参数。
     * @return 输入参数。
     */
    public static ChatMessage assistantMessage(Long conversationId, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.ASSISTANT, content, status, provider, model, errorMessage);
    }

    /**
     * 绑定所属执行记录，确保消息与右侧工作区回放共享同一条 run 主链路。
     * @param runId 执行记录标识。
     * @return 当前消息对象。
     */
    public ChatMessage attachRun(Long runId) {
        this.runId = runId;
        return this;
    }

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param conversationId 输入参数。
     * @param role 输入参数。
     * @param content 输入参数。
     * @param status 输入参数。
     * @param provider 输入参数。
     * @param model 输入参数。
     * @param errorMessage 输入参数。
     * @return 输入参数。
     */
    public static ChatMessage create(Long id, Long conversationId, ChatMessageRole role, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        if (id == null || conversationId == null || role == null || status == null || StrUtil.isBlank(content)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_MESSAGE_FIELDS_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        return ChatMessage.builder()
            .id(id)
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .status(status)
            .provider(provider)
            .model(model)
            .errorMessage(errorMessage)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    /**
     * 恢复持久化后的运行时扩展字段与原始时间，保证消息回放与后续编排可复用。
     * @param runId 所属执行记录标识。
     * @param thinkingContent 深度思考内容。
     * @param thinkingDuration 深度思考耗时（秒）。
     * @param intentCode 命中的意图编码。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     */
    public void restoreRuntimeState(
        Long runId,
        String thinkingContent,
        Integer thinkingDuration,
        String intentCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.runId = runId;
        this.thinkingContent = thinkingContent;
        this.thinkingDuration = thinkingDuration;
        this.intentCode = intentCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
