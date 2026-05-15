package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天 Trace 后台查询服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatTraceService {

    private final ChatTraceRunRepository chatTraceRunRepository;
    private final ChatTraceNodeRepository chatTraceNodeRepository;

    public ConversationTraceView getTrace(String traceId) {
        return new ConversationTraceView(
            chatTraceRunRepository.findByTraceId(traceId).orElseThrow(() -> new IllegalArgumentException("Trace not found")),
            chatTraceNodeRepository.findByTraceId(traceId)
        );
    }

    public List<ChatTraceRun> listRecentTraces() {
        return chatTraceRunRepository.findRecent(50);
    }
}
