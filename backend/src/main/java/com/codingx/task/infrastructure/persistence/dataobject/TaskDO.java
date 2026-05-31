package com.codingx.task.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务表的数据对象映射。
 */
@Data
@TableName("task")
public class TaskDO {

    @TableId("id")
    private Long id; // 任务主键。

    @TableField("title")
    private String title; // 任务标题。

    @TableField("description")
    private String description; // 任务说明，可为空。

    @TableField("status")
    private String status; // 任务状态，映射 TaskStatus 枚举。

    @TableField("runtime_type")
    private String runtimeType; // 任务运行时类型，映射 RuntimeType 枚举。

    @TableField("workspace_id")
    private Long workspaceId; // 任务关联工作空间标识，可为空。

    @TableField("created_by")
    private Long createdBy; // 任务创建人用户标识。

    @TableField("started_at")
    private LocalDateTime startedAt; // 任务开始执行时间，未启动时为空。

    @TableField("finished_at")
    private LocalDateTime finishedAt; // 任务完成或失败时间，未终结时为空。

    @TableField("error_message")
    private String errorMessage; // 任务失败时的错误文案，非失败状态可为空。

    @TableField("summary")
    private String summary; // 任务成功后的执行摘要，可为空。

    @TableField("created_at")
    private LocalDateTime createdAt; // 数据创建时间。

    @TableField("updated_at")
    private LocalDateTime updatedAt; // 数据最近更新时间。

    @TableField("deleted")
    private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
