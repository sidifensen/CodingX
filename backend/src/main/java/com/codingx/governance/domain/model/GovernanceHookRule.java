package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一个任务生命周期 Hook 规则。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernanceHookRule {

    /** Hook 规则主键。 */
    private Long id;
    /** Hook 编码，审计和管理端通过该编码识别规则。 */
    private String hookCode;
    /** Hook 名称，用于页面展示。 */
    private String hookName;
    /** 触发点，例如 BEFORE_TOOL_CALL、AFTER_TOOL_CALL、TASK_COMPLETED。 */
    private String triggerPoint;
    /** 条件关键字，可为空；存在时仅在上下文文本包含该关键字时触发。 */
    private String conditionKeyword;
    /** 动作类型，MVP 仅支持 AUDIT，不执行外部副作用。 */
    private String actionType;
    /** 动作配置 JSON，保留后续扩展空间。 */
    private String actionConfigJson;
    /** 启用状态，1 表示参与触发。 */
    private Integer enabled;
    /** 排序号，数值越小越先触发。 */
    private Integer sortNo;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
