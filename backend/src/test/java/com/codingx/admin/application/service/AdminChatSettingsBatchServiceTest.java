package com.codingx.admin.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.chat.application.service.support.ConfigCryptoService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端运行时配置批量保存服务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatSettingsBatchServiceTest {

    @Mock
    private ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    @Mock
    private RuntimeSettingService runtimeSettingService;

    @Mock
    private ConfigCryptoService configCryptoService;

    @InjectMocks
    private AdminChatSettingsService adminChatSettingsService;

    /**
     * 批量保存应逐条复用单条保存逻辑，并在结束后返回最新全量配置。
     */
    @Test
    void saveAllPersistsEachSettingAndReturnsLatestList() {
        List<ChatRuntimeSetting> incomingSettings = List.of(
            sampleSetting("chat.model", "deepseek-chat"),
            sampleSetting("chat.temperature", "0.7")
        );
        when(runtimeSettingService.listAll()).thenReturn(incomingSettings);

        List<ChatRuntimeSetting> settings = adminChatSettingsService.saveAll(incomingSettings);

        verify(chatRuntimeSettingRepository, times(2)).save(any(ChatRuntimeSetting.class));
        assertEquals(2, settings.size());
        assertEquals("chat.model", settings.getFirst().getSettingKey());
        assertEquals("chat.temperature", settings.get(1).getSettingKey());
    }

    /**
     * 空批量保存不应写库，只返回刷新后的全量配置。
     */
    @Test
    void saveAllWithEmptyListReturnsLatestListWithoutPersisting() {
        when(runtimeSettingService.listAll()).thenReturn(List.of(sampleSetting("chat.model", "deepseek-chat")));

        List<ChatRuntimeSetting> settings = adminChatSettingsService.saveAll(List.of());

        verify(chatRuntimeSettingRepository, times(0)).save(any(ChatRuntimeSetting.class));
        assertEquals(1, settings.size());
        assertEquals("chat.model", settings.getFirst().getSettingKey());
    }

    private ChatRuntimeSetting sampleSetting(String key, String value) {
        return ChatRuntimeSetting.builder()
            .settingKey(key)
            .settingValue(value)
            .valueType("STRING")
            .categoryCode("chat")
            .sortNo(1)
            .restartRequired(false)
            .deleted(0)
            .build();
    }
}
