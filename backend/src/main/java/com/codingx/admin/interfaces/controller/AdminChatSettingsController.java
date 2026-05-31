package com.codingx.admin.interfaces.controller;

import com.codingx.admin.application.service.AdminChatSettingsService;
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

    /**
     * 管理端运行时配置服务，负责配置校验、加密、批量保存和缓存刷新。
     */
    private final AdminChatSettingsService adminChatSettingsService;

    /**
     * 查询所有聊天运行时配置。
     * @return 管理端可展示的配置列表。
     */
    @GetMapping
    public ApiResponse<List<ChatRuntimeSetting>> listSettings() {
        // 步骤 1：配置读取和敏感项脱敏统一交给服务层处理。
        return ApiResponse.success(adminChatSettingsService.listAllSettings());
    }

    /**
     * 保存单条聊天运行时配置。
     * @param setting 待保存配置。
     * @return 保存后的管理端展示配置。
     */
    @PostMapping
    public ApiResponse<ChatRuntimeSetting> saveSetting(@RequestBody ChatRuntimeSetting setting) {
        // 步骤 1：服务层负责参数校验、敏感配置加密和运行时缓存刷新。
        return ApiResponse.success(adminChatSettingsService.save(setting));
    }

    /**
     * 批量保存配置并返回最新配置列表，供管理端“保存覆盖配置”场景一次提交。
     * @param settings 待保存配置集合。
     * @return 最新全量配置。
     */
    @PostMapping("/batch")
    public ApiResponse<List<ChatRuntimeSetting>> saveSettings(@RequestBody List<ChatRuntimeSetting> settings) {
        // 步骤 1：批量循环、空集合处理和最新列表读取全部下沉到服务层。
        return ApiResponse.success(adminChatSettingsService.saveAll(settings));
    }
}
