package com.codingx.mcp.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.mcp.application.executor.ChatMcpToolExecutor;
import com.codingx.mcp.application.executor.ChatMcpToolRegistry;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户侧 MCP 只读查询能力。
 */
@Service
@RequiredArgsConstructor
public class ChatMcpQueryService {

    /**
     * MCP 配置仓储，用于读取管理端启用的 MCP 配置。
     */
    private final ChatMcpRepository chatMcpRepository;

    /**
     * MCP 工具注册表，用于判断配置项是否有真实执行器接入。
     */
    private final ChatMcpToolRegistry chatMcpToolRegistry;

    /**
     * 查询当前启用 MCP 列表，并标记每项是否具备可执行能力。
     * 关键约束：用户端据此禁用“无执行器”的 MCP，避免被错误开启。
     * @return 启用 MCP 列表（附带 available 标记）。
     */
    public List<ChatMcp> listEnabledMcps() {
        // 步骤 1：读取当前服务实例已注册执行器，生成可用能力集合。
        Set<String> registeredToolIds = chatMcpToolRegistry.all().stream()
            .map(ChatMcpToolExecutor::toolId)
            .collect(Collectors.toSet());
        // 步骤 2：只返回管理端启用的 MCP，并过滤掉编码异常的脏数据。
        // 步骤 3：available 是运行态字段，内置 MCP 按执行器注册判断，外部 MCP 按发现成功的 schema 快照判断。
        return chatMcpRepository.findAllEnabled().stream()
            .filter(mcp -> mcp != null && mcp.getMcpCode() != null)
            .map(mcp -> mcp.toBuilder().available(isRuntimeAvailable(mcp, registeredToolIds)).build())
            .toList();
    }

    private boolean isRuntimeAvailable(ChatMcp mcp, Set<String> registeredToolIds) {
        if ("external".equalsIgnoreCase(StrUtil.blankToDefault(mcp.getSourceType(), ""))) {
            return "AVAILABLE".equalsIgnoreCase(StrUtil.blankToDefault(mcp.getHealthStatus(), ""))
                && StrUtil.isNotBlank(mcp.getToolSchemaJson());
        }
        return registeredToolIds.contains(mcp.getMcpCode());
    }
}
