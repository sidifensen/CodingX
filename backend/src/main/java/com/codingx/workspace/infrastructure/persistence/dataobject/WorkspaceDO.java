package com.codingx.workspace.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作空间数据库映射对象，对应 workspace 表。
 */
@Data
@TableName("workspace")
public class WorkspaceDO {

    /**
     * 工作空间主键，创建时由应用服务生成；前后端以该值关联会话和任务。
     */
    @TableId("id")
    private Long id;

    /**
     * 工作空间展示名称，管理端列表、会话侧栏和默认空间展示都会使用。
     */
    @TableField("name")
    private String name;

    /**
     * 关联代码仓库地址，可为空；云端代码类任务可用该值定位远程仓库。
     */
    @TableField("repository_url")
    private String repositoryUrl;

    /**
     * 工作空间对应分支名称，可为空；仓库绑定后用于展示或后续检出分支。
     */
    @TableField("branch_name")
    private String branchName;

    /**
     * 本地工作目录，可为空；local 运行目标下用于绑定用户本地仓库路径。
     */
    @TableField("working_directory")
    private String workingDirectory;

    /**
     * 运行目标类型，通常为 cloud 或 local；决定会话和任务默认绑定策略。
     */
    @TableField("runtime_target")
    private String runtimeTarget;

    /**
     * 兼容旧调用方的空间类型内存值。
     * 业务语义已经统一由 runtime_target 承载，这里仅保留给上层对象复用，不再映射数据库列。
     */
    @TableField(exist = false)
    private String workspaceType;

    /**
     * 创建人用户标识，用于工作空间归属校验和管理端筛选。
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 数据创建时间，由仓储保存时写入；用于管理端列表展示。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 数据最近更新时间，工作空间名称、路径或运行目标变化时刷新。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    @TableField("deleted")
    private Integer deleted;
}
