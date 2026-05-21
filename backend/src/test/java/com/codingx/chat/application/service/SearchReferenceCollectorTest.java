package com.codingx.chat.application.service;

import static org.mockito.Mockito.verify;

import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证参考来源收集器会将搜索结果落成可回放记录。
 */
@ExtendWith(MockitoExtension.class)
class SearchReferenceCollectorTest {

    @Mock
    private ChatMessageReferenceRepository chatMessageReferenceRepository;

    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    @InjectMocks
    private SearchReferenceCollector searchReferenceCollector;

    /**
     * 搜索结果应逐条持久化为参考来源记录。
     */
    @Test
    void collectPersistsReferenceRecords() {
        searchReferenceCollector.collect(1001L, 2001L, 3001L, List.of(
            new SearchReferenceCandidate("A", "https://a", "site", "snippet")
        ));

        ArgumentCaptor<ChatMessageReference> captor = ArgumentCaptor.forClass(ChatMessageReference.class);
        verify(chatMessageReferenceRepository).save(captor.capture());
        verify(chatStreamPublisher).publishReference(org.mockito.ArgumentMatchers.eq(3001L), org.mockito.ArgumentMatchers.any());
    }
}

