package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatQueryTermMappingService;
import com.codingx.chat.interfaces.request.QueryTermMappingCreateRequest;
import com.codingx.chat.interfaces.request.QueryTermMappingUpdateRequest;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.chat.interfaces.response.QueryTermMappingResponse;
import com.codingx.common.model.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供关键词映射后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/query-term-mappings")
@RequiredArgsConstructor
public class AdminChatQueryTermMappingController {

    private final AdminChatQueryTermMappingService adminChatQueryTermMappingService;

    /**
     * 分页查询映射规则，支持源词/目标词关键字过滤。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param keyword 搜索关键字。
     * @return 分页结果。
     */
    @GetMapping
    public ApiResponse<PageResult<QueryTermMappingResponse>> pageQuery(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminChatQueryTermMappingService.pageQuery(current, size, keyword));
    }

    /**
     * 查询映射规则详情。
     * @param id 主键。
     * @return 映射规则。
     */
    @GetMapping("/{id}")
    public ApiResponse<QueryTermMappingResponse> queryById(@PathVariable Long id) {
        return ApiResponse.success(adminChatQueryTermMappingService.queryById(id));
    }

    /**
     * 创建映射规则。
     * @param request 请求对象。
     * @return 创建结果。
     */
    @PostMapping
    public ApiResponse<QueryTermMappingResponse> createMapping(@RequestBody QueryTermMappingCreateRequest request) {
        return ApiResponse.success(adminChatQueryTermMappingService.create(request));
    }

    /**
     * 更新映射规则。
     * @param id 主键。
     * @param request 请求对象。
     * @return 更新结果。
     */
    @PutMapping("/{id}")
    public ApiResponse<QueryTermMappingResponse> updateMapping(@PathVariable Long id, @RequestBody QueryTermMappingUpdateRequest request) {
        return ApiResponse.success(adminChatQueryTermMappingService.update(id, request));
    }

    /**
     * 删除映射规则。
     * @param id 主键。
     * @return 删除结果。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMapping(@PathVariable Long id) {
        adminChatQueryTermMappingService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }

}
