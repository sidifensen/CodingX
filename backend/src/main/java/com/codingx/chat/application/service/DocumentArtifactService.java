package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责生成最小文档产物记录，后续可替换为真实 docx 写入器。
 */
@Service
@RequiredArgsConstructor
public class DocumentArtifactService {

    private final ChatMessageArtifactRepository chatMessageArtifactRepository;

    /**
     * 生成 docx 产物记录并持久化。
     * @param runId 运行标识。
     * @param messageId 消息标识。
     * @param conversationId 会话标识。
     * @param content 文档内容。
     */
    public void createDocxArtifact(Long runId, Long messageId, Long conversationId, String content) {
        chatMessageArtifactRepository.save(ChatMessageArtifact.builder()
            .id(IdUtil.getSnowflakeNextId())
            .runId(runId)
            .messageId(messageId)
            .conversationId(conversationId)
            .artifactType("docx")
            .name("search-report.docx")
            .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .storagePath("artifacts/" + conversationId + "/search-report.docx")
            .contentPreview(content)
            .createdAt(LocalDateTime.now())
            .build());
    }
}
