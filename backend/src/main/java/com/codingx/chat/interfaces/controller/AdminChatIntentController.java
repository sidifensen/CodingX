package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatIntentService;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供意图树后台管理接口，兼容旧列表/保存入口并补齐树形 CRUD 能力。
 */
@RestController
@RequestMapping("/api/admin/chat/intents")
@RequiredArgsConstructor
public class AdminChatIntentController {

    private final AdminChatIntentService adminChatIntentService;

    /**
     * 返回平铺意图列表，保留既有管理端调用路径。
     * @return 标准接口响应。
     */
    @GetMapping
    public ApiResponse<List<ChatIntentNode>> listIntents() {
        return ApiResponse.success(adminChatIntentService.listAllNodes());
    }

    /**
     * 返回树形意图列表，供 ragent 风格配置台直接渲染左侧树。
     * @return 标准接口响应。
     */
    @GetMapping("/tree")
    public ApiResponse<List<ChatIntentNode>> listIntentTree() {
        return ApiResponse.success(adminChatIntentService.listTree());
    }

    /**
     * 创建节点，同时兼容旧前端通过 POST 保存完整节点的调用习惯。
     * @param node 请求节点。
     * @return 标准接口响应。
     */
    @PostMapping
    public ApiResponse<ChatIntentNode> saveIntent(@RequestBody ChatIntentNode node) {
        return ApiResponse.success(adminChatIntentService.save(node));
    }

    /**
     * 按主键更新节点，路径 ID 优先于请求体 ID。
     * @param id 节点主键。
     * @param node 请求节点。
     * @return 标准接口响应。
     */
    @PutMapping("/{id}")
    public ApiResponse<ChatIntentNode> updateIntent(@PathVariable Long id, @RequestBody ChatIntentNode node) {
        return ApiResponse.success(adminChatIntentService.update(id, node));
    }

    /**
     * 按主键逻辑删除节点，服务层负责子节点保护。
     * @param id 节点主键。
     * @return 标准接口响应。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteIntent(@PathVariable Long id) {
        adminChatIntentService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }
}
