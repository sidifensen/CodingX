package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatIntentService;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供意图树后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/intents")
@RequiredArgsConstructor
public class AdminChatIntentController {

    private final AdminChatIntentService adminChatIntentService;

    @GetMapping
    public ApiResponse<List<ChatIntentNode>> listIntents() {
        return ApiResponse.success(adminChatIntentService.listAllNodes());
    }

    @PostMapping
    public ApiResponse<ChatIntentNode> saveIntent(@RequestBody ChatIntentNode node) {
        return ApiResponse.success(adminChatIntentService.save(node));
    }
}
