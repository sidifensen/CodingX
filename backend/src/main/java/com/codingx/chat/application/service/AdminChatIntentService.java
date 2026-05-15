package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供意图树后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatIntentService {

    private final ChatIntentNodeRepository chatIntentNodeRepository;

    public List<ChatIntentNode> listAllNodes() {
        return chatIntentNodeRepository.findAllNodes();
    }

    public ChatIntentNode save(ChatIntentNode node) {
        ChatIntentNode persisted = node.toBuilder()
            .id(node.getId() == null ? IdUtil.getSnowflakeNextId() : node.getId())
            .createdAt(node.getCreatedAt() == null ? LocalDateTime.now() : node.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(node.getDeleted() == null ? 0 : node.getDeleted())
            .build();
        chatIntentNodeRepository.save(persisted);
        return persisted;
    }
}
