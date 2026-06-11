package com.codingx.cli.agent;

/**
 * 危险命令审批回写能力，只有连接真实后端的事件源需要实现。
 */
public interface PermissionApprovalResolver {

    /**
     * 将用户在 CLI 中作出的即时审批决定提交给后端。
     *
     * @param requestId 后端 SSE `approval` 事件下发的一次性审批请求 ID。
     * @param decision 审批决定，只允许 `ALLOW` 或 `DENY`。
     */
    void resolvePermissionApproval(String requestId, String decision);
}
