package com.codingx.mcp.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条可在聊天链路中启用的 MCP 配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMcp {

    private Long id;
    private String mcpCode;
    private String displayName;
    private String description;
    private String category;
    private String sourceType;
    private Integer enabled;
    /**
     * 用户侧运行时可用状态（非持久化字段）。
     * true 表示当前服务实例存在对应执行器，可被用户开启。
     */
    private Boolean available;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
