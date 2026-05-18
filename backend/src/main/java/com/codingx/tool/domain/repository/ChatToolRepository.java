package com.codingx.tool.domain.repository;

import com.codingx.tool.domain.model.ChatTool;
import java.util.List;

/**
 * 定义聊天工具配置仓储能力。
 */
public interface ChatToolRepository {

    /**
     * 查询全部未删除工具配置。
     * @return 工具配置列表。
     */
    List<ChatTool> findAll();

    /**
     * 根据主键查询工具配置。
     * @param id 主键。
     * @return 工具配置，未命中返回 null。
     */
    ChatTool findById(Long id);

    /**
     * 根据工具编码查询工具配置。
     * @param toolCode 工具编码。
     * @return 工具配置，未命中返回 null。
     */
    ChatTool findByToolCode(String toolCode);

    /**
     * 检查工具编码是否存在，更新场景可排除当前主键。
     * @param toolCode 工具编码。
     * @param excludedId 排除主键。
     * @return 是否存在。
     */
    boolean existsByToolCode(String toolCode, Long excludedId);

    /**
     * 保存工具配置（新增或更新）。
     * @param chatTool 工具配置实体。
     */
    void save(ChatTool chatTool);

    /**
     * 逻辑删除工具配置。
     * @param id 主键。
     */
    void softDeleteById(Long id);
}
