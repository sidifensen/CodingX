package com.codingx.chat.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天消息表的数据对象映射。
 */
@Data
@TableName("chat_message")
public class ChatMessageDO {
    @TableId("id") private Long id; // 消息主键。
    @TableField("conversation_id") private Long conversationId; // 所属会话主键。
    @TableField("run_id") private Long runId; // 产生该消息的执行 run 标识。
    @TableField("role") private String role; // 消息角色，映射 USER 或 ASSISTANT。
    @TableField("content") private String content; // 消息正文内容。
    @TableField("thinking_content") private String thinkingContent; // 模型深度思考内容，可为空。
    @TableField("thinking_duration") private Integer thinkingDuration; // 深度思考耗时，单位秒，可为空。
    @TableField("status") private String status; // 消息状态，映射 ChatMessageStatus。
    @TableField("provider") private String provider; // 生成助手消息的模型供应商，可为空。
    @TableField("model") private String model; // 生成助手消息的模型标识，可为空。
    @TableField("error_message") private String errorMessage; // 消息生成失败时的错误文案。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
    @TableField("created_at") private LocalDateTime createdAt; // 消息创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 消息最近更新时间。
}
