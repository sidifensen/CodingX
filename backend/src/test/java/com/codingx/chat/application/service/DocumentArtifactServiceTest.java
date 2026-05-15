package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证文档产物服务会生成并持久化最小 docx 产物记录。
 */
@ExtendWith(MockitoExtension.class)
class DocumentArtifactServiceTest {

    @Mock
    private ChatMessageArtifactRepository chatMessageArtifactRepository;

    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private DocumentArtifactService documentArtifactService;

    /**
     * 生成文档时应返回 docx 产物并写入仓储。
     */
    @Test
    void createDocxArtifactPersistsArtifactRecord() {
        org.mockito.Mockito.when(fileStorageService.saveArtifact(eq(3001L), eq("search-report.docx"), eq("整理后的报告内容")))
            .thenReturn(new StoredArtifact("storage/docx/3001-search-report.docx", "/files/storage/docx/3001-search-report.docx"));

        documentArtifactService.createDocxArtifact(1001L, 2001L, 3001L, "整理后的报告内容");

        ArgumentCaptor<ChatMessageArtifact> captor = ArgumentCaptor.forClass(ChatMessageArtifact.class);
        verify(chatMessageArtifactRepository).save(captor.capture());
        verify(chatStreamPublisher).publishArtifact(org.mockito.ArgumentMatchers.eq(3001L), org.mockito.ArgumentMatchers.any());
        assertEquals("docx", captor.getValue().getArtifactType());
        assertEquals("storage/docx/3001-search-report.docx", captor.getValue().getStoragePath());
    }
}
