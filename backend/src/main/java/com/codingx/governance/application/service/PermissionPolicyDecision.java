package com.codingx.governance.application.service;

/**
 * 权限策略判定结果，供工具执行链路决定是否继续执行真实工具。
 *
 * @param action 策略动作，支持 ALLOW、CONFIRM、DENY。
 * @param result 审计结果，例如 ALLOWED、DENIED、CONFIRM_REQUIRED。
 * @param riskLevel 风险等级。
 * @param matchedPolicyCode 命中的策略编码，未命中时为空。
 * @param message 返回给前端或审计展示的中文说明。
 */
public record PermissionPolicyDecision(
    String action,
    String result,
    String riskLevel,
    String matchedPolicyCode,
    String message
) {
}
