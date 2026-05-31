package com.codingx.workspace.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 WorkspaceDO 的数据库字段映射。
 */
@Data
@TableName("workspace")
public class WorkspaceDO {

    /**
     * 主键标识。
     */
    @TableId("id")
    private Long id;

    /**
     * 工作空间名称。
     */
    @TableField("name")
    private String name;

    /**
     * 关联代码仓库地址。
     */
    @TableField("repository_url")
    private String repositoryUrl;

    /**
     * 工作空间对应分支名称。
     */
    @TableField("branch_name")
    private String branchName;

    /**
     * 本地工作目录。
     */
    @TableField("working_directory")
    private String workingDirectory;

    /**
     * 运行目标类型。
     */
    @TableField("runtime_target")
    private String runtimeTarget;

    /**
     * 空间类型的兼容内存字段。
     * 业务语义已经统一由 runtime_target 承载，这里仅保留给上层对象复用，不再映射数据库列。
     */
    @TableField(exist = false)
    private String workspaceType;

    /**
     * 创建人用户标识。
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    @TableField("deleted")
    private Integer deleted;
}
