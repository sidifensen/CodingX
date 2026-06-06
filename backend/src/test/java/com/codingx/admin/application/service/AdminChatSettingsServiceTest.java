package com.codingx.admin.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.support.ConfigCryptoService;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端系统配置服务对运行时缓存与列表数据的一致性处理。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatSettingsServiceTest {

    @Mock
    private ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    @Mock
    private RuntimeSettingService runtimeSettingService;

    @Mock
    private ConfigCryptoService configCryptoService;

    @InjectMocks
    private AdminChatSettingsService adminChatSettingsService;

    /**
     * 管理端刷新列表前必须先重载运行时缓存，确保外部改库后的配置分类也能立即展示。
     */
    @Test
    void listAllSettingsRefreshesRuntimeCacheBeforeReturningList() {
        ChatRuntimeSetting setting = ChatRuntimeSetting.builder()
            .id(1L)
            .settingKey("chat.tool.max_rounds")
            .settingValue("10000")
            .valueType("INTEGER")
            .categoryCode("本地工具调用")
            .description("本地工具调用最大轮次")
            .sortNo(10)
            .restartRequired(false)
            .deleted(0)
            .build();
        when(runtimeSettingService.listAll()).thenReturn(List.of(setting));

        List<ChatRuntimeSetting> settings = adminChatSettingsService.listAllSettings();

        InOrder runtimeCacheOrder = inOrder(runtimeSettingService);
        runtimeCacheOrder.verify(runtimeSettingService).refresh();
        runtimeCacheOrder.verify(runtimeSettingService).listAll();
        assertEquals("本地工具调用", settings.getFirst().getCategoryCode());
    }

    /**
     * 敏感配置写入时必须加密并写入脱敏值，避免后台把明文密钥直接落库。
     */
    @Test
    void saveEncryptsSecretSetting() {
        ChatRuntimeSetting secretSetting = ChatRuntimeSetting.builder()
            .settingKey("ai.providers.siliconflow.api_key")
            .settingValue("plain-secret")
            .valueType("STRING")
            .secret(true)
            .build();
        when(configCryptoService.encrypt("plain-secret")).thenReturn("cipher-secret");
        when(configCryptoService.mask("plain-secret")).thenReturn("pla***cret");

        ChatRuntimeSetting saved = adminChatSettingsService.save(secretSetting);

        assertEquals("", saved.getSettingValue());
        assertNull(saved.getEncryptedValue());
        assertEquals("pla***cret", saved.getMaskedValue());
        ArgumentCaptor<ChatRuntimeSetting> persistedCaptor = ArgumentCaptor.forClass(ChatRuntimeSetting.class);
        verify(chatRuntimeSettingRepository).save(persistedCaptor.capture());
        assertEquals("cipher-secret", persistedCaptor.getValue().getEncryptedValue());
    }

    /**
     * 管理端列表展示敏感配置时，只允许返回脱敏值，禁止继续把密文挂回接口响应。
     */
    @Test
    void listAllSettingsMasksSecretValues() {
        ChatRuntimeSetting secretSetting = ChatRuntimeSetting.builder()
            .id(2L)
            .settingKey("ai.providers.siliconflow.api_key")
            .settingValue("")
            .encryptedValue("cipher-secret")
            .secret(true)
            .maskedValue("sk-****")
            .valueType("STRING")
            .categoryCode("ai.providers")
            .description("硅基流动接口密钥")
            .build();
        when(runtimeSettingService.listAll()).thenReturn(List.of(secretSetting));

        List<ChatRuntimeSetting> settings = adminChatSettingsService.listAllSettings();

        assertEquals("", settings.getFirst().getSettingValue());
        assertEquals("sk-****", settings.getFirst().getMaskedValue());
        assertNull(settings.getFirst().getEncryptedValue());
    }

    /**
     * 敏感配置在管理端留空时表示保持原值，不应把空字符串重新加密覆盖旧密文。
     */
    @Test
    void saveKeepsExistingCipherWhenSecretValueLeftBlank() {
        ChatRuntimeSetting existing = ChatRuntimeSetting.builder()
            .id(9L)
            .settingKey("ai.providers.siliconflow.api_key")
            .settingValue("")
            .encryptedValue("cipher-secret")
            .secret(true)
            .maskedValue("sk-****")
            .valueType("STRING")
            .build();
        ChatRuntimeSetting incoming = ChatRuntimeSetting.builder()
            .id(9L)
            .settingKey("ai.providers.siliconflow.api_key")
            .settingValue("")
            .secret(true)
            .maskedValue("sk-****")
            .valueType("STRING")
            .build();
        when(runtimeSettingService.listAll()).thenReturn(List.of(existing));

        ChatRuntimeSetting saved = adminChatSettingsService.save(incoming);

        assertNull(saved.getEncryptedValue());
        assertEquals("sk-****", saved.getMaskedValue());
        ArgumentCaptor<ChatRuntimeSetting> persistedCaptor = ArgumentCaptor.forClass(ChatRuntimeSetting.class);
        verify(chatRuntimeSettingRepository).save(persistedCaptor.capture());
        assertEquals("cipher-secret", persistedCaptor.getValue().getEncryptedValue());
        verify(configCryptoService, never()).encrypt("");
    }
}
