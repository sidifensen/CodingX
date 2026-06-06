package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一次本地工具权限判定审计记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernancePermissionAudit {

    /** 权限审计主键，写入时由服务生成。 */
    private Long id;
    /** 触发工具调用的用户 ID，可为空。 */
    private Long userId;
    /** 触发工具调用的会话 ID，可为空。 */
    private Long conversationId;
    /** 触发工具调用的运行 ID，可为空。 */
    private Long runId;
    /** 被判定的工具编码。 */
    private String toolCode;
    /** 工具原始输入，保留审计所需摘要。 */
    private String toolInput;
    /** 工具执行工作目录。 */
    private String workingDirectory;
    /** 命中的策略编码，未命中策略时为空。 */
    private String matchedPolicyCode;
    /** 策略判定动作，支持 ALLOW、CONFIRM、DENY。 */
    private String decision;
    /** 本次判定风险等级。 */
    private String riskLevel;
    /** 处理结果，例如 ALLOWED、DENIED、CONFIRM_REQUIRED。 */
    private String result;
    /** 返回给前端或管理端的中文说明。 */
    private String message;
    /** 审计创建时间。 */
    private LocalDateTime createdAt;
}
