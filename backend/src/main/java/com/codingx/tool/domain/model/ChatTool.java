package com.codingx.tool.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条可在管理端维护的 Codex 工具配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatTool {

    /**
     * 工具配置主键。
     */
    private Long id;

    /**
     * 工具编码，模型工具调用和管理端配置通过该编码定位执行器。
     */
    private String toolCode;

    /**
     * 工具展示名称。
     */
    private String displayName;

    /**
     * 工具能力描述，供管理端列表和模型工具说明复用。
     */
    private String description;

    /**
     * 工具分类，用于管理端筛选和前端分组。
     */
    private String category;

    /**
     * 工具来源类型，区分 Codex 内置、MCP 或外部扩展。
     */
    private String sourceType;

    /**
     * 启用状态，1 表示允许聊天运行时调用。
     */
    private Integer enabled;

    /**
     * 排序号，数值越小越靠前。
     */
    private Integer sortNo;

    /**
     * 配置创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 配置最近更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    private Integer deleted;
}
