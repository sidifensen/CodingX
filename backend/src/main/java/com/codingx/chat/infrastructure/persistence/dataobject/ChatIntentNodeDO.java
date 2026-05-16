package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义意图树节点表的数据对象映射，显式保留运行时字段与管理端扩展字段。
 */
@Data
@TableName("chat_intent_node")
public class ChatIntentNodeDO {
    @TableId("id") private Long id;
    @TableField("intent_code") private String intentCode;
    @TableField("parent_code") private String parentCode;
    @TableField("name") private String name;
    @TableField("description") private String description;
    @TableField("intent_type") private String intentType;
    @TableField("kb_id") private Long kbId;
    @TableField("level") private Integer level;
    @TableField("examples") private String examples;
    @TableField("collection_name") private String collectionName;
    @TableField("top_k") private Integer topK;
    @TableField("kind") private Integer kind;
    @TableField("prompt_template") private String promptTemplate;
    @TableField("mcp_tool_id") private String mcpToolId;
    @TableField("param_prompt_template") private String paramPromptTemplate;
    @TableField("prompt_snippet") private String promptSnippet;
    @TableField("enabled") private Integer enabled;
    @TableField("sort_no") private Integer sortNo;
    @TableField("sort_order") private Integer sortOrder;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
