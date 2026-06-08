package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatWorkspaceInventoryService;
import com.codingx.chat.interfaces.response.ChatWorkspaceResponse;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户侧工作区库存接口，供聊天侧栏展示当前用户全部有效工作区。
 */
@RestController
@RequestMapping("/api/chat/workspaces")
@RequiredArgsConstructor
public class ChatWorkspaceInventoryController {

    /** 工作区库存服务，负责按登录用户读取有效工作区并完成响应投影。 */
    private final ChatWorkspaceInventoryService chatWorkspaceInventoryService;

    /**
     * 查询当前用户拥有的有效工作区列表，不创建或修改任何工作区状态。
     * @return 当前用户工作区库存。
     */
    @GetMapping
    public ApiResponse<List<ChatWorkspaceResponse>> listWorkspaces() {
        // Controller 只负责协议层封装，归属过滤和字段投影交给服务层。
        return ApiResponse.success(chatWorkspaceInventoryService.listCurrentUserWorkspaces());
    }
}
