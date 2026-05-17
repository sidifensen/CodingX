package com.codingx.auth.interfaces.request;

/**
 * 定义管理端用户状态更新请求字段。
 * @param status 目标状态。
 */
public record AdminUserStatusUpdateRequest(
    String status
) {
}
