package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责把搜索结果转换成可回放的参考来源记录。
 */
@Service
@RequiredArgsConstructor
public class SearchReferenceCollector {

    private final ChatMessageReferenceRepository chatMessageReferenceRepository;

    /**
     * 将搜索结果落库为参考来源记录。
     * @param runId 运行标识。
     * @param messageId 消息标识。
     * @param conversationId 会话标识。
     * @param candidates 搜索候选结果。
     */
    public void collect(Long runId, Long messageId, Long conversationId, List<SearchReferenceCandidate> candidates) {
        int rank = 1;
        for (SearchReferenceCandidate candidate : candidates) {
            chatMessageReferenceRepository.save(ChatMessageReference.builder()
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
                .build());
        }
    }
}
