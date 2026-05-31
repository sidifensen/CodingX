package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示意图树中的单个配置节点，兼容聊天运行时旧字段与管理端配置字段。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatIntentNode {

    /**
     * 节点主键，新增请求入库前可为空，持久化时由应用服务生成。
     */
    private Long id;

    /**
     * 意图编码，运行时路由和树形父子关系的稳定标识，业务上要求唯一且不能为空。
     */
    private String intentCode;

    /**
     * 父节点意图编码；为空表示根节点，指向不存在父节点时管理端会按孤儿根节点展示。
     */
    private String parentCode;

    /**
     * 管理端展示名称，保存时不能为空，用于配置列表和树节点标题。
     */
    private String name;

    /**
     * 意图说明，可为空，用于管理端帮助维护者理解节点用途。
     */
    private String description;

    /**
     * 运行时意图类型，取值如 search、system、mcp；与 kind 互相兼容派生。
     */
    private String intentType;

    /**
     * 关联知识库 ID，仅检索类意图需要；为空表示该节点不绑定固定知识库。
     */
    private Long kbId;

    /**
     * 树节点层级，根节点通常为 0；历史数据缺失时由应用服务按父节点关系兜底。
     */
    private Integer level;

    /**
     * 示例问法文本，可为空，用于辅助配置和后续意图识别提示。
     */
    private String examples;

    /**
     * 向量集合名称，可为空；检索链路可用该字段定位外部知识集合。
     */
    private String collectionName;

    /**
     * 检索返回条数上限，可为空；为空时由下游检索服务使用默认值。
     */
    private Integer topK;

    /**
     * 管理端节点类型枚举，0 表示搜索，1 表示系统，2 表示 MCP；与 intentType 同步。
     */
    private Integer kind;

    /**
     * 节点级提示词模板，可为空；运行时进入该意图时用于构建模型提示。
     */
    private String promptTemplate;

    /**
     * MCP 工具标识，仅 MCP 类型节点使用；为空时该节点不会绑定具体工具。
     */
    private String mcpToolId;

    /**
     * 参数抽取提示词模板，可为空；MCP 工具执行前可用它让模型抽取结构化参数。
     */
    private String paramPromptTemplate;

    /**
     * 提示词片段，可为空；用于拼接到运行时提示词而不替换完整模板。
     */
    private String promptSnippet;

    /**
     * 启用状态，1 表示启用，0 表示停用；为空时保存逻辑按启用处理。
     */
    private Integer enabled;

    /**
     * 旧排序字段，兼容历史运行时读取；保存时与 sortOrder 写入相同排序值。
     */
    private Integer sortNo;

    /**
     * 管理端排序字段，数值越小越靠前；为空时回退 sortNo 或默认 0。
     */
    private Integer sortOrder;

    /**
     * 创建时间，新增时由应用服务补齐；历史数据缺失时保存逻辑会兜底当前时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，每次管理端保存或更新节点时刷新。
     */
    private LocalDateTime updatedAt;

    /**
     * 软删除标记，0 表示有效，1 表示已删除；查询默认只返回有效节点。
     */
    private Integer deleted;

    /**
     * 仅用于管理端树形接口输出，不落库；运行时平铺查询仍使用 parentCode 关系。
     */
    private List<ChatIntentNode> children;
}
