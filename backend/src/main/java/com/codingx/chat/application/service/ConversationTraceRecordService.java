package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责保存聊天链路的根 Trace 与关键节点。
 */
@Service
@RequiredArgsConstructor
public class ConversationTraceRecordService {

    private final ChatTraceRunRepository chatTraceRunRepository;
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
        ChatTraceRun traceRun = ConversationTraceContext.start(traceName, conversationId, userId).toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .entryMethod("chat")
            .startedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatTraceRunRepository.save(traceRun);
        chatTraceNodeRepository.save(ChatTraceNode.builder()
            .id(IdUtil.getSnowflakeNextId())
            .traceId(traceRun.getTraceId())
            .nodeId(IdUtil.fastSimpleUUID())
            .depth(0)
            .nodeType("entry")
            .nodeName(traceName)
            .status("RUNNING")
            .startedAt(now)
            .createdAt(now)
            .build());
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
            .orElseThrow(() -> new IllegalArgumentException("Trace not found"));
        LocalDateTime startedAt = traceRun.getStartedAt() != null ? traceRun.getStartedAt() : now;
        chatTraceRunRepository.save(traceRun.toBuilder()
            .taskId(taskId)
            .status(status)
            .errorMessage(errorMessage)
            .finishedAt(now)
            .updatedAt(now)
            .durationMs(java.time.Duration.between(startedAt, now).toMillis())
            .build());

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
}
