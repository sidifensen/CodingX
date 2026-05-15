package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天运行时配置后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSettingsService {

    private final ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    public List<ChatRuntimeSetting> listAllSettings() {
        return chatRuntimeSettingRepository.findAll();
    }

    public ChatRuntimeSetting save(ChatRuntimeSetting setting) {
        ChatRuntimeSetting persisted = setting.toBuilder()
            .id(setting.getId() == null ? IdUtil.getSnowflakeNextId() : setting.getId())
            .createdAt(setting.getCreatedAt() == null ? LocalDateTime.now() : setting.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(setting.getDeleted() == null ? 0 : setting.getDeleted())
            .build();
        chatRuntimeSettingRepository.save(persisted);
        return persisted;
    }
}
