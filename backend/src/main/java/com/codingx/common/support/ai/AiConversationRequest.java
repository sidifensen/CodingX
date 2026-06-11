package com.codingx.common.support.ai;

import cn.hutool.core.collection.CollUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.tool.application.service.ChatToolSpec;
import java.util.List;
import lombok.Builder;

/**
 * 统一模型层的对话请求对象，屏蔽上层业务与具体 provider 之间的入参差异。
 *
 * @param messages 本轮会话上下文消息列表，至少包含一条消息。
 * @param attachments 本轮用户消息关联附件列表，可为空；图片附件会影响模型候选过滤。
 * @param tools 暴露给模型的工具定义列表，可为空。
 * @param preferredModel 前端或运行时显式指定的候选模型 ID，可为空。
 * @param stream 是否请求流式响应，true 表示 provider 需要按 token/事件回调。
 * @param thinkingEnabled 是否开启深度思考模式，影响候选模型过滤和默认模型选择。
 * @param streamCompletionTimeoutOverrideMs 本次请求覆盖的首包后流式空闲超时毫秒，可为空；表示允许的最长连续静默时间，用于目标模式等需要更快识别挂起的链路。
 */
@Builder(toBuilder = true)
public record AiConversationRequest(
    List<ChatMessage> messages, // 本轮会话上下文消息列表，至少包含一条消息。
    List<ChatAttachment> attachments, // 本轮用户消息关联附件列表，可为空；图片附件会影响模型候选过滤。
    List<ChatToolSpec> tools, // 暴露给模型的工具定义列表，可为空。
    String preferredModel, // 前端或运行时显式指定的候选模型 ID，可为空。
    boolean stream, // 是否请求流式响应，true 表示 provider 需要按 token/事件回调。
    boolean thinkingEnabled, // 是否开启深度思考模式，影响候选模型过滤和默认模型选择。
    Long streamCompletionTimeoutOverrideMs // 本次请求覆盖的首包后流式空闲超时，空值表示使用全局路由配置。
) {

    /**
     * 校验请求至少带有一条消息，避免 provider 层处理无效输入。
     */
    public AiConversationRequest {
        // 步骤 1：模型 provider 至少需要一条上下文消息，空消息请求直接在统一请求对象处拒绝。
        if (CollUtil.isEmpty(messages)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AI_CONVERSATION_MESSAGES_REQUIRED);
        }
    }
}

