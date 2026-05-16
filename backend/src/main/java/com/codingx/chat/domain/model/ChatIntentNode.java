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

    private Long id;
    private String intentCode;
    private String parentCode;
    private String name;
    private String description;
    private String intentType;
    private Long kbId;
    private Integer level;
    private String examples;
    private String collectionName;
    private Integer topK;
    private Integer kind;
    private String promptTemplate;
    private String mcpToolId;
    private String paramPromptTemplate;
    private String promptSnippet;
    private Integer enabled;
    private Integer sortNo;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    /**
     * 仅用于管理端树形接口输出，不落库；运行时平铺查询仍使用 parentCode 关系。
     */
    private List<ChatIntentNode> children;
}
