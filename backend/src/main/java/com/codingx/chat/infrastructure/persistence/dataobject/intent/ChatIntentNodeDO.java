package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 意图树节点表的数据对象映射，显式保留运行时识别配置与管理端扩展配置。
 */
@Data
@TableName("chat_intent_node")
public class ChatIntentNodeDO {
    @TableId("id") private Long id; // 意图节点主键。
    @TableField("intent_code") private String intentCode; // 意图编码，运行时识别和前端树节点都依赖该值。
    @TableField("parent_code") private String parentCode; // 父级意图编码，根节点可为空。
    @TableField("name") private String name; // 意图展示名称。
    @TableField("description") private String description; // 意图说明，用于模型分类提示和管理端展示。
    @TableField("intent_type") private String intentType; // 意图类型，区分普通对话、知识库、MCP 等分支。
    @TableField("kb_id") private Long kbId; // 关联知识库主键，非知识库意图可为空。
    @TableField("level") private Integer level; // 意图树层级，根节点通常为 1。
    @TableField("examples") private String examples; // 示例问法文本，用于意图识别提示。
    @TableField("collection_name") private String collectionName; // 向量集合名称，知识库检索场景使用。
    @TableField("top_k") private Integer topK; // 知识库检索返回条数上限。
    @TableField("kind") private Integer kind; // 管理端扩展类型标记。
    @TableField("prompt_template") private String promptTemplate; // 命中意图后的系统提示词模板。
    @TableField("mcp_tool_id") private String mcpToolId; // 关联 MCP 工具标识，非 MCP 意图可为空。
    @TableField("param_prompt_template") private String paramPromptTemplate; // MCP 参数抽取提示词模板。
    @TableField("prompt_snippet") private String promptSnippet; // 管理端展示用提示词片段。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示参与运行时识别。
    @TableField("sort_no") private Integer sortNo; // 排序号，数值越小越靠前。
    @TableField("sort_order") private Integer sortOrder; // 兼容历史排序值，旧管理端仍可能读取该列。
    @TableField("created_at") private LocalDateTime createdAt; // 节点创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 节点最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
