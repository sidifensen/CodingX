package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示最终展示给用户的参考来源记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageReference {

    /** 参考来源主键，数据库生成，新建来源时为空。 */
    private Long id;
    /** 来源所属执行运行 ID，用于关联搜索或 MCP 过程。 */
    private Long runId;
    /** 来源关联的助手消息 ID，消息落库前可为空。 */
    private Long messageId;
    /** 来源所属会话 ID，用于按会话回放参考资料。 */
    private Long conversationId;
    /** 来源类型，如 web、mcp、document，用于区分引用来源。 */
    private String sourceType;
    /** 来源标题，展示给前端引用卡片使用。 */
    private String title;
    /** 来源链接，可为空，文档或本地工具来源可能没有外部地址。 */
    private String url;
    /** 来源站点名称，可为空，通常由 URL 域名或搜索结果返回。 */
    private String siteName;
    /** 来源摘要片段，用于说明该来源命中的关键信息。 */
    private String snippet;
    /** 展示排序号，数值越小越靠前。 */
    private Integer rankNo;
    /** 扩展元数据 JSON，保存得分、原始 provider 等非固定字段。 */
    private String metadataJson;
    /** 来源创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
}
