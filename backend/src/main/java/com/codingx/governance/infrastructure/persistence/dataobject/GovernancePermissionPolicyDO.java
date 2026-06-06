package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 权限策略表数据对象，负责把治理策略领域字段映射到数据库列。
 */
@Data
@TableName("governance_permission_policy")
public class GovernancePermissionPolicyDO {

    @TableId("id") private Long id; // 权限策略主键ID。
    @TableField("policy_code") private String policyCode; // 策略编码，审计命中时回写该值。
    @TableField("policy_name") private String policyName; // 策略名称，供管理端展示。
    @TableField("tool_code") private String toolCode; // 匹配工具编码，空值表示适用全部工具。
    @TableField("command_pattern") private String commandPattern; // 匹配命令片段，主要约束 shell/bash 类输入。
    @TableField("path_pattern") private String pathPattern; // 匹配路径片段，主要约束文件写入和补丁类输入。
    @TableField("action") private String action; // 策略动作，支持 ALLOW、CONFIRM、DENY。
    @TableField("risk_level") private String riskLevel; // 风险等级，写入审计供管理端筛选。
    @TableField("description") private String description; // 策略说明，记录业务意图和边界。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示参与判定。
    @TableField("sort_no") private Integer sortNo; // 策略排序号，数值越小越优先匹配。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
