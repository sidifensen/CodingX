package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.admin.application.service.AdminChatSettingsService;
import com.codingx.admin.interfaces.controller.AdminChatSettingsController;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端聊天运行时配置接口的 HTTP 契约与 Controller 边界。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatSettingsControllerTest {

    @Mock
    private AdminChatSettingsService adminChatSettingsService;

    @InjectMocks
    private AdminChatSettingsController adminChatSettingsController;

    /**
     * 列表接口应直接返回服务层提供的运行时配置。
     */
    @Test
    void listSettingsReturnsRuntimeSettings() throws Exception {
        when(adminChatSettingsService.listAllSettings()).thenReturn(List.of(sampleSetting("chat.model", "deepseek-chat")));

        mockMvc().perform(get("/api/admin/chat/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].settingKey").value("chat.model"))
            .andExpect(jsonPath("$.data[0].settingValue").value("deepseek-chat"));
    }

    /**
     * 批量保存接口应委托服务层统一处理循环保存和最新列表返回。
     */
    @Test
    void saveSettingsDelegatesBatchToService() throws Exception {
        when(adminChatSettingsService.saveAll(anyList())).thenReturn(List.of(
            sampleSetting("chat.model", "deepseek-chat"),
            sampleSetting("chat.temperature", "0.7")
        ));

        mockMvc().perform(post("/api/admin/chat/settings/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [
                      {"settingKey":"chat.model","settingValue":"deepseek-chat","valueType":"STRING"},
                      {"settingKey":"chat.temperature","settingValue":"0.7","valueType":"DECIMAL"}
                    ]
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].settingKey").value("chat.model"))
            .andExpect(jsonPath("$.data[1].settingKey").value("chat.temperature"));

        verify(adminChatSettingsService).saveAll(anyList());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatSettingsController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private ChatRuntimeSetting sampleSetting(String key, String value) {
        return ChatRuntimeSetting.builder()
            .id(1L)
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
