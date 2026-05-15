package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatQueryTermMappingService;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供关键词映射后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/query-term-mappings")
@RequiredArgsConstructor
public class AdminChatQueryTermMappingController {

    private final AdminChatQueryTermMappingService adminChatQueryTermMappingService;

    @GetMapping
    public ApiResponse<List<ChatQueryTermMapping>> listMappings() {
        return ApiResponse.success(adminChatQueryTermMappingService.listAllMappings());
    }

    @PostMapping
    public ApiResponse<ChatQueryTermMapping> saveMapping(@RequestBody ChatQueryTermMapping mapping) {
        return ApiResponse.success(adminChatQueryTermMappingService.save(mapping));
    }
}
