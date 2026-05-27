package com.codingx.admin.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
