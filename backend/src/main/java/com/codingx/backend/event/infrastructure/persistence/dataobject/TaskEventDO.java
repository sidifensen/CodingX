package com.codingx.backend.event.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("cx_task_event")
public class TaskEventDO {
    @TableId("id") private Long id;
    @TableField("task_id") private Long taskId;
    @TableField("event_type") private String eventType;
    @TableField("sequence_no") private Long sequenceNo;
    @TableField("title") private String title;
    @TableField("content") private String content;
    @TableField("metadata_json") private String metadataJson;
    @TableField("created_at") private LocalDateTime createdAt;
}