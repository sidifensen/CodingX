package com.codingx.admin.interfaces.controller;

import com.codingx.chat.application.service.AdminChatSettingsService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天运行时配置后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/settings")
@RequiredArgsConstructor
public class AdminChatSettingsController {

    private final AdminChatSettingsService adminChatSettingsService;

    @GetMapping
    public ApiResponse<List<ChatRuntimeSetting>> listSettings() {
        return ApiResponse.success(adminChatSettingsService.listAllSettings());
    }

    @PostMapping
    public ApiResponse<ChatRuntimeSetting> saveSetting(@RequestBody ChatRuntimeSetting setting) {
        return ApiResponse.success(adminChatSettingsService.save(setting));
    }

    /**
     * 批量保存配置并返回最新配置列表，供管理端“保存覆盖配置”场景一次提交。
     * @param settings 待保存配置集合。
     * @return 最新全量配置。
     */
    @PostMapping("/batch")
    public ApiResponse<List<ChatRuntimeSetting>> saveSettings(@RequestBody List<ChatRuntimeSetting> settings) {
        if (settings != null) {
            for (ChatRuntimeSetting setting : settings) {
                adminChatSettingsService.save(setting);
            }
        }
        return ApiResponse.success(adminChatSettingsService.listAllSettings());
    }
}
