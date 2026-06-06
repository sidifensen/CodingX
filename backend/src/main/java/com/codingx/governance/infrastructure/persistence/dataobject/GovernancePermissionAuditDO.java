package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 权限审计表数据对象，记录一次本地工具调用被策略判定的结果。
 */
@Data
@TableName("governance_permission_audit")
public class GovernancePermissionAuditDO {

    @TableId("id") private Long id; // 权限审计主键ID。
    @TableField("user_id") private Long userId; // 触发工具调用的用户 ID，可为空。
    @TableField("conversation_id") private Long conversationId; // 触发工具调用的会话 ID，可为空。
    @TableField("run_id") private Long runId; // 触发工具调用的运行 ID，可为空。
    @TableField("tool_code") private String toolCode; // 被判定的工具编码。
    @TableField("tool_input") private String toolInput; // 工具原始输入摘要。
    @TableField("working_directory") private String workingDirectory; // 工具执行工作目录。
    @TableField("matched_policy_code") private String matchedPolicyCode; // 命中的策略编码，未命中时为空。
    @TableField("decision") private String decision; // 策略判定动作。
    @TableField("risk_level") private String riskLevel; // 本次判定风险等级。
    @TableField("result") private String result; // 处理结果，例如 ALLOWED、DENIED、CONFIRM_REQUIRED。
    @TableField("message") private String message; // 返回给用户或管理端的中文说明。
    @TableField("created_at") private LocalDateTime createdAt; // 审计创建时间。
}
