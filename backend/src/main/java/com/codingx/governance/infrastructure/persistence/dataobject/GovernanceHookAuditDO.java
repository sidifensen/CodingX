package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Hook 审计表数据对象，记录生命周期 Hook 的实际触发结果。
 */
@Data
@TableName("governance_hook_audit")
public class GovernanceHookAuditDO {

    @TableId("id") private Long id; // Hook 审计主键ID。
    @TableField("hook_code") private String hookCode; // 命中的 Hook 编码。
    @TableField("trigger_point") private String triggerPoint; // 本次触发点。
    @TableField("conversation_id") private Long conversationId; // 触发会话 ID，可为空。
    @TableField("run_id") private Long runId; // 触发运行 ID，可为空。
    @TableField("tool_code") private String toolCode; // 关联工具编码，可为空。
    @TableField("status") private String status; // 触发状态，例如 SUCCESS、FAILED。
    @TableField("message") private String message; // 审计说明或失败原因。
    @TableField("created_at") private LocalDateTime createdAt; // 审计创建时间。
}
