package com.codingx.admin.interfaces.controller;

import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.model.ApiResponse;
import com.codingx.expert.application.service.AdminChatExpertService;
import com.codingx.expert.domain.model.ChatExpert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天专家后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/experts")
@RequiredArgsConstructor
public class AdminChatExpertController {

    private final AdminChatExpertService adminChatExpertService;

    /**
     * 分页查询专家列表。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 专家分页结果。
     */
    @GetMapping
    public ApiResponse<PageResult<ChatExpert>> listExperts(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(adminChatExpertService.pageExperts(current, size));
    }

    @PostMapping
    public ApiResponse<ChatExpert> createExpert(@RequestBody ChatExpert request) {
        return ApiResponse.success(adminChatExpertService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatExpert> updateExpert(@PathVariable Long id, @RequestBody ChatExpert request) {
        return ApiResponse.success(adminChatExpertService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpert(@PathVariable Long id) {
        adminChatExpertService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }
}
