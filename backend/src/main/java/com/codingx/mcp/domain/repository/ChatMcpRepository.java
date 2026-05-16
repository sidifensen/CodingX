package com.codingx.mcp.domain.repository;

import com.codingx.mcp.domain.model.ChatMcp;
import java.util.List;

/**
 * 定义聊天 MCP 配置仓储能力。
 */
public interface ChatMcpRepository {

    /**
     * 查询全部未删除 MCP。
     * @return MCP 列表。
     */
    List<ChatMcp> findAll();

    /**
     * 查询全部启用 MCP。
     * @return MCP 列表。
     */
    List<ChatMcp> findAllEnabled();

    /**
     * 通过主键查询 MCP。
     * @param id 主键。
     * @return MCP，未命中返回 null。
     */
    ChatMcp findById(Long id);

    /**
     * 通过编码查询 MCP。
     * @param mcpCode MCP 编码。
     * @return MCP，未命中返回 null。
     */
    ChatMcp findByMcpCode(String mcpCode);

    /**
     * 检查编码是否存在，更新场景可排除当前主键。
     * @param mcpCode MCP 编码。
     * @param excludedId 排除主键。
     * @return 是否存在。
     */
    boolean existsByMcpCode(String mcpCode, Long excludedId);

    /**
     * 保存 MCP（新增或更新）。
     * @param chatMcp MCP 实体。
     */
    void save(ChatMcp chatMcp);

    /**
     * 逻辑删除指定 MCP。
     * @param id 主键。
     */
    void softDeleteById(Long id);

    /**
     * 查询任务绑定的 MCP。
     * @param taskId 任务标识。
     * @return MCP 列表。
     */
    List<ChatMcp> findByTaskId(Long taskId);

    /**
     * 绑定任务与 MCP 编码列表。
     * @param taskId 任务标识。
     * @param mcpCodes MCP 编码列表。
     */
    void bindTaskMcps(Long taskId, List<String> mcpCodes);
}
