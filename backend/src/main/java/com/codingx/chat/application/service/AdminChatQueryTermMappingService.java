package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供关键词映射后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatQueryTermMappingService {

    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;

    public List<ChatQueryTermMapping> listAllMappings() {
        return chatQueryTermMappingRepository.findAllMappings();
    }

    public ChatQueryTermMapping save(ChatQueryTermMapping mapping) {
        ChatQueryTermMapping persisted = mapping.toBuilder()
            .id(mapping.getId() == null ? IdUtil.getSnowflakeNextId() : mapping.getId())
            .createdAt(mapping.getCreatedAt() == null ? LocalDateTime.now() : mapping.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(mapping.getDeleted() == null ? 0 : mapping.getDeleted())
            .build();
        chatQueryTermMappingRepository.save(persisted);
        return persisted;
    }
}
