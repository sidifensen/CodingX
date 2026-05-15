package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatRuntimeSetting;
import java.util.List;

/**
 * 定义聊天运行时配置仓储能力。
 */
public interface ChatRuntimeSettingRepository {

    List<ChatRuntimeSetting> findAll();

    void save(ChatRuntimeSetting setting);
}
