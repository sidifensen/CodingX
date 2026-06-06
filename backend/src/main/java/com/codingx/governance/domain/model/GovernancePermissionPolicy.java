package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条本地工具执行权限策略。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernancePermissionPolicy {

    /** 权限策略主键，数据库持久化标识。 */
    private Long id;
    /** 策略编码，管理端和审计记录通过该编码识别命中规则。 */
    private String policyCode;
    /** 策略名称，用于管理端列表展示。 */
    private String policyName;
    /** 匹配工具编码，可为空；为空表示适用于所有工具。 */
    private String toolCode;
    /** 匹配命令片段，可为空；用于识别 shell、bash、exec_command 等命令输入。 */
    private String commandPattern;
    /** 匹配路径片段，可为空；用于识别文件写入、编辑、补丁等路径风险。 */
    private String pathPattern;
    /** 策略动作，支持 ALLOW、CONFIRM、DENY。 */
    private String action;
    /** 风险等级，例如 LOW、MEDIUM、HIGH。 */
    private String riskLevel;
    /** 策略说明，解释业务意图和边界。 */
    private String description;
    /** 启用状态，1 表示参与策略判定。 */
    private Integer enabled;
    /** 排序号，数值越小优先级越高。 */
    private Integer sortNo;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
