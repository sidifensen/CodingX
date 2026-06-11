package com.codingx.governance.application.service;

import com.codingx.common.exception.BusinessException;

/**
 * 危险命令需要用户确认时抛出的业务异常，额外携带一次性审批请求快照。
 */
public class PermissionApprovalRequiredException extends BusinessException {

    /** 本次危险命令审批请求，前端和 CLI 通过 requestId 回传用户决定。 */
    private final PermissionApprovalRequest request;

    /**
     * 创建需要确认的业务异常。
     *
     * @param request 已创建的审批请求快照。
     */
    public PermissionApprovalRequiredException(PermissionApprovalRequest request) {
        super("GOVERNANCE_PERMISSION_CONFIRM_REQUIRED", request == null ? "当前操作需要确认后再执行" : request.message());
        this.request = request;
    }

    /**
     * 获取审批请求快照。
     *
     * @return 审批请求。
     */
    public PermissionApprovalRequest request() {
        return request;
    }
}
