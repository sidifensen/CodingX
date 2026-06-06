package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目画像表数据对象，保存工作空间扫描后的技术栈和验证命令摘要。
 */
@Data
@TableName("governance_project_profile")
public class GovernanceProjectProfileDO {

    @TableId("id") private Long id; // 项目画像主键ID。
    @TableField("workspace_id") private Long workspaceId; // 关联工作空间 ID。
    @TableField("workspace_path") private String workspacePath; // 被扫描的工作空间路径。
    @TableField("summary") private String summary; // 项目画像摘要。
    @TableField("tech_stack_json") private String techStackJson; // 技术栈 JSON 文本。
    @TableField("entrypoints_json") private String entrypointsJson; // 入口文件 JSON 文本。
    @TableField("verification_commands_json") private String verificationCommandsJson; // 建议验证命令 JSON 文本。
    @TableField("status") private String status; // 扫描状态，例如 COMPLETED、FAILED。
    @TableField("scanned_at") private LocalDateTime scannedAt; // 扫描完成时间。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
