package com.codingx.governance.application.service;

/**
 * 危险命令审批请求状态，约束一次性请求从待处理到终态的生命周期。
 */
public enum PermissionApprovalStatus {
    /** 请求已创建，正在等待用户在桌面或终端确认。 */
    PENDING,
    /** 用户已允许，请求还未被工具执行链路消费。 */
    ALLOWED,
    /** 用户已拒绝，本次工具调用必须失败。 */
    DENIED,
    /** 请求等待超时或被服务端清理，本次工具调用必须失败。 */
    EXPIRED,
    /** 允许结果已经被匹配工具调用消费，不能再次复用。 */
    CONSUMED
}
