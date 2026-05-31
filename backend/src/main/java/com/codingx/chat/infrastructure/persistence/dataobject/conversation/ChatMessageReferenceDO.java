package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义参考来源表的数据对象映射。
 */
@Data
@TableName("chat_message_reference")
public class ChatMessageReferenceDO {
    @TableId("id") private Long id; // 参考来源主键。
    @TableField("run_id") private Long runId; // 产生该引用的执行 run 标识。
    @TableField("message_id") private Long messageId; // 引用关联的助手消息主键。
    @TableField("conversation_id") private Long conversationId; // 引用所属会话主键。
    @TableField("source_type") private String sourceType; // 来源类型，例如 web_search 或 mcp。
    @TableField("title") private String title; // 引用标题，可为空。
    @TableField("url") private String url; // 引用 URL，可为空。
    @TableField("site_name") private String siteName; // 站点名称，可为空。
    @TableField("snippet") private String snippet; // 引用摘要片段。
    @TableField("rank_no") private Integer rankNo; // 引用排序号，数值越小越靠前。
    @TableField("metadata_json") private String metadataJson; // 引用扩展元数据 JSON。
    @TableField("created_at") private LocalDateTime createdAt; // 引用创建时间。
}
