package com.codingx.expert.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.expert.application.service.ChatExpertQueryService;
import com.codingx.expert.domain.model.ChatExpert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户侧聊天专家查询接口。
 */
@RestController
@RequestMapping({"/api/experts", "/api/chat/experts"})
@RequiredArgsConstructor
public class ChatExpertController {

    /**
     * 专家查询服务，负责读取用户侧可选专家列表。
     */
    private final ChatExpertQueryService chatExpertQueryService;

    /**
     * 查询当前启用专家列表，兼容用户侧旧路径与新路径。
     * @return 专家列表。
     */
    @GetMapping
    public ApiResponse<List<ChatExpert>> listEnabledExperts() {
        // 步骤 1：Controller 只做 HTTP 响应封装，专家筛选和排序交给查询服务。
        return ApiResponse.success(chatExpertQueryService.listEnabledExperts());
    }
}
