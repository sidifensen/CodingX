package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Hook 规则表数据对象，承载工具调用前后和任务完成等生命周期触发配置。
 */
@Data
@TableName("governance_hook_rule")
public class GovernanceHookRuleDO {

    @TableId("id") private Long id; // Hook 规则主键ID。
    @TableField("hook_code") private String hookCode; // Hook 编码，审计记录通过该值关联规则。
    @TableField("hook_name") private String hookName; // Hook 名称，供管理端展示。
    @TableField("trigger_point") private String triggerPoint; // Hook 触发点，例如 BEFORE_TOOL_CALL。
    @TableField("condition_keyword") private String conditionKeyword; // 条件关键字，空值表示触发点命中即记录。
    @TableField("action_type") private String actionType; // 动作类型，MVP 仅允许 AUDIT。
    @TableField("action_config_json") private String actionConfigJson; // 动作配置 JSON，保留后续扩展。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示参与触发。
    @TableField("sort_no") private Integer sortNo; // 排序号，数值越小越先触发。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
