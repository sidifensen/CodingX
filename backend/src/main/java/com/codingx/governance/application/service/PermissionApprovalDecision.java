package com.codingx.governance.application.service;

/**
 * 危险命令审批决定，只表达用户对单次请求的明确处理结果。
 */
public enum PermissionApprovalDecision {
    /** 用户允许本次危险命令继续执行。 */
    ALLOW,
    /** 用户拒绝本次危险命令继续执行。 */
    DENY
}
