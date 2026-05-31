package com.codingx.auth.interfaces.request;

/**
 * 管理端编辑用户资料请求体。
 * @param displayName 更新后的展示名称，编辑时必填。
 * @param userType 更新后的用户类型枚举名称，可为空；为空时保留原类型。
 * @param status 更新后的用户状态枚举名称，可为空；为空时保留原状态。
 * @param email 更新后的邮箱，可为空；非空时服务层会做唯一性检查。
 * @param phone 更新后的手机号，可为空；当前只做首尾空白规整。
 * @param avatarUrl 更新后的头像地址，可为空；当前只做首尾空白规整。
 */
public record AdminUserUpdateRequest(
    String displayName, // 更新后的展示名称，编辑时必填。
    String userType, // 更新后的用户类型枚举名称，可为空；为空时保留原类型。
    String status, // 更新后的用户状态枚举名称，可为空；为空时保留原状态。
    String email, // 更新后的邮箱，可为空；非空时服务层会做唯一性检查。
    String phone, // 更新后的手机号，可为空；当前只做首尾空白规整。
    String avatarUrl // 更新后的头像地址，可为空；当前只做首尾空白规整。
) {
}
