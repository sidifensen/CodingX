package com.codingx.governance.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.governance.application.service.SlashCommandService;
import com.codingx.governance.domain.model.GovernanceSlashCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户端聊天输入区可见的 Slash Command 命令目录接口。
 */
@RestController
@RequestMapping("/api/chat/slash-commands")
@RequiredArgsConstructor
public class SlashCommandController {

    /** Slash Command 服务，负责过滤启用命令并保持命令配置语义一致。 */
    private final SlashCommandService slashCommandService;

    /**
     * 查询当前用户端可选择的启用命令。
     * @return Slash Command 列表。
     */
    @GetMapping
    public ApiResponse<List<GovernanceSlashCommand>> listEnabledCommands() {
        // 步骤 1：Controller 只做协议适配，启用态、排序和命令语义由服务层处理。
        return ApiResponse.success(slashCommandService.listEnabledCommands());
    }
}
