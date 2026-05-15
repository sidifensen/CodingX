package com.codingx.chat.interfaces.controller;

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
}
