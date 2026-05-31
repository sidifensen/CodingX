package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责把搜索结果转换成可回放的参考来源记录。
 */
@Service
@RequiredArgsConstructor
public class SearchReferenceCollector {

    /** 消息引用仓储，用于把本轮搜索来源持久化到消息和 run 维度。 */
    private final ChatMessageReferenceRepository chatMessageReferenceRepository;
    /** 流事件发布器，用于向前端推送来源列表更新事件。 */
    private final com.codingx.chat.domain.port.ChatStreamPublisher chatStreamPublisher;

    /**
     * 将搜索结果落库为参考来源记录。
     * @param runId 运行标识。
     * @param messageId 消息标识。
     * @param conversationId 会话标识。
     * @param candidates 搜索候选结果。
     */
    @ConversationTraceNode(name = "reference-collect", type = "SEARCH")
    public void collect(Long runId, Long messageId, Long conversationId, List<SearchReferenceCandidate> candidates) {
        int rank = 1;
        for (SearchReferenceCandidate candidate : candidates) {
            ChatMessageReference reference = ChatMessageReference.builder()
                .id(IdUtil.getSnowflakeNextId())
                .runId(runId)
                .messageId(messageId)
                .conversationId(conversationId)
                .sourceType("web")
                .title(candidate.title())
                .url(candidate.url())
                .siteName(candidate.siteName())
                .snippet(candidate.snippet())
                .rankNo(rank++)
                .createdAt(LocalDateTime.now())
                .build();
            chatMessageReferenceRepository.save(reference);
            chatStreamPublisher.publishReference(conversationId, Map.of(
                "id", reference.getId(),
                "runId", reference.getRunId(),
                "messageId", reference.getMessageId(),
                "conversationId", reference.getConversationId(),
                "sourceType", reference.getSourceType(),
                "title", reference.getTitle(),
                "url", reference.getUrl(),
                "siteName", reference.getSiteName(),
                "snippet", reference.getSnippet(),
                "rankNo", reference.getRankNo()
            ));
        }
    }
}

