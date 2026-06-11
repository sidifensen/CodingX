package com.codingx.governance.application.service;

import java.time.LocalDateTime;

/**
 * 危险命令一次性审批请求快照。
 *
 * @param requestId 审批请求 ID，前端和 CLI 回传该值完成允许或拒绝。
 * @param userId 创建请求的用户 ID，可为空；为空时只允许本地无登录测试链路使用。
 * @param conversationId 创建请求的会话 ID，可为空。
 * @param runId 创建请求的运行 ID，可为空。
 * @param toolCode 被拦截的工具编码。
 * @param toolInput 被拦截的原始工具输入 JSON 或文本。
 * @param commandText 从工具输入中提取的命令文本，提取失败时回退原输入摘要。
 * @param workingDirectory 工具执行工作目录，可为空。
 * @param matchedPolicyCode 命中的策略编码。
 * @param riskLevel 风险等级。
 * @param message 展示给用户的中文确认提示。
 * @param status 当前审批状态。
 * @param createdAt 请求创建时间。
 * @param expiresAt 请求过期时间。
 * @param resolvedAt 用户作出决定的时间，可为空。
 */
public record PermissionApprovalRequest(
    String requestId,
    Long userId,
    Long conversationId,
    Long runId,
    String toolCode,
    String toolInput,
    String commandText,
    String workingDirectory,
    String matchedPolicyCode,
    String riskLevel,
    String message,
    PermissionApprovalStatus status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime resolvedAt
) {
    /**
     * 生成带新状态的不可变快照，避免调用方直接修改内存中的请求对象。
     *
     * @param nextStatus 新状态。
     * @param nextResolvedAt 状态变更时间，可为空。
     * @return 更新后的请求快照。
     */
    public PermissionApprovalRequest withStatus(PermissionApprovalStatus nextStatus, LocalDateTime nextResolvedAt) {
        return new PermissionApprovalRequest(
            requestId,
            userId,
            conversationId,
            runId,
            toolCode,
            toolInput,
            commandText,
            workingDirectory,
            matchedPolicyCode,
            riskLevel,
            message,
            nextStatus,
            createdAt,
            expiresAt,
            nextResolvedAt
        );
    }
}
