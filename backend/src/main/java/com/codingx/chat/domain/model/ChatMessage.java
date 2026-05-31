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
 * 聊天消息领域对象，承载用户消息、助手回复、思考内容和模型运行元数据。
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
     * 消息角色，区分用户输入、助手回复等上下文身份。
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
     * 逻辑删除标记；消息级删除只隐藏历史，不物理清理关联运行记录。
     */
    private Integer deleted;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建用户消息。
     * @param conversationId 会话标识。
     * @param content 用户输入正文。
     * @return 已初始化为完成状态的用户消息。
     */
    public static ChatMessage userMessage(Long conversationId, String content) {
        // 步骤 1：用户消息进入上下文时即视为完成，不需要 provider/model/error 元数据。
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.USER, content, ChatMessageStatus.COMPLETED, null, null, null);
    }

    /**
     * 创建助手消息。
     * @param conversationId 会话标识。
     * @param content 助手回复正文或错误占位内容。
     * @param status 消息状态，表示生成中、完成或失败等业务终态。
     * @param provider 实际命中的模型供应商，可为空。
     * @param model 实际命中的模型名称，可为空。
     * @param errorMessage 失败时返回给前端展示的中文错误文案，可为空。
     * @return 助手消息领域对象。
     */
    public static ChatMessage assistantMessage(Long conversationId, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        // 步骤 1：助手消息保留 provider/model/error 元数据，便于历史回放和故障排查。
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
     * 创建聊天消息领域对象。
     * @param id 消息主键。
     * @param conversationId 会话标识。
     * @param role 消息角色。
     * @param content 消息正文，不允许为空白。
     * @param status 消息状态。
     * @param provider 实际命中的模型供应商，可为空。
     * @param model 实际命中的模型名称，可为空。
     * @param errorMessage 失败原因文案，可为空。
     * @return 初始化完成的聊天消息领域对象。
     */
    public static ChatMessage create(Long id, Long conversationId, ChatMessageRole role, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        // 步骤 1：领域对象必须具备主键、会话、角色、状态和正文，缺失时直接拒绝创建。
        if (id == null || conversationId == null || role == null || status == null || StrUtil.isBlank(content)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_MESSAGE_FIELDS_REQUIRED);
        }
        // 步骤 2：新建消息使用当前时间初始化创建与更新时间，持久化恢复路径会覆盖原始时间。
        LocalDateTime now = LocalDateTime.now();
        // 步骤 3：默认 deleted=0，消息级删除只能通过仓储逻辑删除更新。
        return ChatMessage.builder()
            .id(id)
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .status(status)
            .provider(provider)
            .model(model)
            .errorMessage(errorMessage)
            .deleted(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    /**
     * 恢复持久化后的运行时扩展字段与原始时间，保证消息回放与后续编排可复用。
     * @param runId 所属执行记录标识。
     * @param thinkingContent 深度思考内容。
     * @param thinkingDuration 深度思考耗时（秒）。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     */
    public void restoreRuntimeState(
        Long runId,
        String thinkingContent,
        Integer thinkingDuration,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        // 步骤 1：运行态字段来自数据库恢复，不参与新消息的必填校验。
        this.runId = runId;
        this.thinkingContent = thinkingContent;
        this.thinkingDuration = thinkingDuration;
        // 步骤 2：保留数据库中的原始时间，避免历史回放时被当前时间覆盖。
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
