package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责保存聊天链路的根 Trace 与关键节点。
 */
@Service
@RequiredArgsConstructor
public class ConversationTraceRecordService {

    /** Trace 运行仓储，用于创建、更新和关闭一次会话执行链路。 */
    private final ChatTraceRunRepository chatTraceRunRepository;
    /** Trace 节点仓储，用于写入每个注解节点的状态、耗时和错误信息。 */
    private final ChatTraceNodeRepository chatTraceNodeRepository;

    /**
     * 启动一条新的聊天 Trace。
     * @param traceName 链路名称。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @return 根 Trace。
     */
    public ChatTraceRun startTrace(String traceName, Long conversationId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ChatTraceRun traceRun = ConversationTraceContext.start(traceName, conversationId, userId)
            .withStartMetadata(IdUtil.getSnowflakeNextId(), "chat", now, now, now, 0);
        chatTraceRunRepository.save(traceRun);
        ChatTraceNode rootNode = ChatTraceNode.builder()
            .id(IdUtil.getSnowflakeNextId())
            .traceId(traceRun.getTraceId())
            .nodeId(IdUtil.fastSimpleUUID())
            .depth(0)
            .nodeType("entry")
            .nodeName(traceName)
            .status("RUNNING")
            .startedAt(now)
            .createdAt(now)
            .build();
        chatTraceNodeRepository.save(rootNode);
        ConversationTraceContext.pushNode(rootNode.getNodeId());
        return traceRun;
    }

    /**
     * 将当前链路收口为终态，并同步更新入口节点状态与任务标识。
     * @param traceId 链路标识。
     * @param taskId 关联任务标识。
     * @param status 终态状态值。
     * @param errorMessage 错误信息。
     */
    public void finishTrace(String traceId, Long taskId, String status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        ChatTraceRun traceRun = chatTraceRunRepository.findByTraceId(traceId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_TRACE_NOT_FOUND));
        LocalDateTime startedAt = traceRun.getStartedAt() != null ? traceRun.getStartedAt() : now;
        chatTraceRunRepository.save(traceRun.finish(
            taskId,
            status,
            errorMessage,
            now,
            java.time.Duration.between(startedAt, now).toMillis()
        ));

        for (ChatTraceNode node : chatTraceNodeRepository.findByTraceId(traceId)) {
            if (!"entry".equals(node.getNodeType())) {
                continue;
            }
            LocalDateTime nodeStartedAt = node.getStartedAt() != null ? node.getStartedAt() : now;
            chatTraceNodeRepository.save(node.toBuilder()
                .status(status)
                .errorMessage(errorMessage)
                .finishedAt(now)
                .durationMs(java.time.Duration.between(nodeStartedAt, now).toMillis())
                .build());
        }
        ConversationTraceContext.clear();
    }

    /**
     * 在当前 trace 下启动一个子节点。
     * @param nodeName 节点名称。
     * @param nodeType 节点类型。
     * @param className 类名。
     * @param methodName 方法名。
     * @param startedAt 开始时间。
     * @return 已持久化的节点。
     */
    public ChatTraceNode startNode(String nodeName, String nodeType, String className, String methodName, LocalDateTime startedAt) {
        ChatTraceRun traceRun = ConversationTraceContext.current();
        if (traceRun == null) {
            throw new IllegalStateException(ErrorMessageCatalog.CHAT_TRACE_CONTEXT_NOT_AVAILABLE);
        }
        ChatTraceNode traceNode = ChatTraceNode.builder()
            .id(IdUtil.getSnowflakeNextId())
            .traceId(traceRun.getTraceId())
            .nodeId(IdUtil.fastSimpleUUID())
            .parentNodeId(ConversationTraceContext.currentNodeId())
            .depth(ConversationTraceContext.currentDepth())
            .nodeType(nodeType)
            .nodeName(nodeName)
            .className(className)
            .methodName(methodName)
            .status("RUNNING")
            .startedAt(startedAt)
            .createdAt(startedAt)
            .build();
        chatTraceNodeRepository.save(traceNode);
        ConversationTraceContext.pushNode(traceNode.getNodeId());
        return traceNode;
    }

    /**
     * 收口一个 Trace 子节点。
     * @param traceNode 目标节点。
     * @param status 状态。
     * @param errorMessage 错误信息。
     * @param durationMs 耗时。
     */
    public void finishNode(ChatTraceNode traceNode, String status, String errorMessage, long durationMs) {
        chatTraceNodeRepository.save(traceNode.toBuilder()
            .status(status)
            .errorMessage(errorMessage)
            .durationMs(durationMs)
            .finishedAt(LocalDateTime.now())
            .build());
        ConversationTraceContext.popNode();
    }
}
